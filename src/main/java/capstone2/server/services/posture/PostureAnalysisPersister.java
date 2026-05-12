package capstone2.server.services.posture;

import capstone2.server.domain.MetricEvaluation;
import capstone2.server.dto.DetailedReportDto;
import capstone2.server.dto.LlmResponseDto;
import capstone2.server.dto.PostureAnalysisResponse;
import capstone2.server.dto.ReportDto;
import capstone2.server.entities.RunSession;
import capstone2.server.repositories.RunSessionRepository;
import capstone2.server.services.DetailedReportService;
import capstone2.server.services.ReportService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostureAnalysisPersister {

    private final ReportService reportService;
    private final DetailedReportService detailService;
    private final RunSessionRepository runSessionRepository;

    @Transactional
    public PostureAnalysisResponse persist(Long runSessionId,
                                           List<MetricEvaluation> evals,
                                           LlmResponseDto llm,
                                           String overlayS3Url) {

        RunSession runSession = runSessionRepository.findById(runSessionId)
                .orElseThrow(() -> new EntityNotFoundException("RunSession not found: " + runSessionId));

        if (overlayS3Url != null && !overlayS3Url.isBlank()) {
            runSession.setVideoS3Url(overlayS3Url);
            runSessionRepository.save(runSession);
        }

        ReportDto report = reportService.create(ReportDto.builder()
                .runId(runSessionId)
                .totalFeedback(llm.totalFeedback())
                .build());

        List<DetailedReportDto> details = new ArrayList<>();
        for (MetricEvaluation eval : evals) {
            details.add(detailService.create(toDetailDto(eval, llm, report.getId())));
        }

        return PostureAnalysisResponse.builder()
                .reportId(report.getId())
                .runSessionId(runSessionId)
                .overlayS3Url(overlayS3Url)
                .totalFeedback(llm.totalFeedback())
                .details(details)
                .build();
    }

    private DetailedReportDto toDetailDto(MetricEvaluation eval, LlmResponseDto llm, Long reportId) {
        LlmResponseDto.PerMetric llmPart = llm.findByType(eval.metricName()).orElse(null);
        return DetailedReportDto.builder()
                .reportId(reportId)
                .type(eval.metricName())
                .status(eval.status())
                .measured(formatMeasured(eval))
                .stdVal(eval.stdVal() == null ? "0" : String.valueOf(eval.stdVal()))
                .refMin(eval.refMin())
                .refMax(eval.refMax())
                .sensitivity(eval.sensitivity())
                .problem(eval.problem())
                .summary(llmPart == null ? null : llmPart.summary())
                .improved(llmPart == null ? null : llmPart.improved())
                .build();
    }

    private String formatMeasured(MetricEvaluation eval) {
        Double v = eval.rawValue();
        if (v == null) return "";
        return String.valueOf(v);
    }
}
