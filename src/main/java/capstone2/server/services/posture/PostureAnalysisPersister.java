package capstone2.server.services.posture;

import capstone2.server.dto.DetailedReportDto;
import capstone2.server.dto.LlmResponseDto;
import capstone2.server.dto.ReportDto;
import capstone2.server.services.DetailedReportService;
import capstone2.server.services.ReportService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 자세 분석 결과를 Report + DetailedReport 로 영속화한다.
 * v3에서는 overlay URL 갱신을 다루지 않는다(별도 콜백 API 담당).
 */
@Service
@RequiredArgsConstructor
public class PostureAnalysisPersister {

    private static final Logger log = LoggerFactory.getLogger(PostureAnalysisPersister.class);

    private final ReportService reportService;
    private final DetailedReportService detailService;

    /**
     * @return 생성된 Report id
     */
    @Transactional
    public Long persist(Long runSessionId, List<PostureMetricView> views, LlmResponseDto llm) {
        ReportDto report = reportService.create(ReportDto.builder()
                .runId(runSessionId)
                .totalFeedback(llm.totalFeedback())
                .build());

        for (PostureMetricView view : views) {
            detailService.create(toDetailDto(view, llm, report.getId()));
        }

        log.info("Posture report persisted: reportId={}, runSessionId={}, details={}",
                report.getId(), runSessionId, views.size());
        return report.getId();
    }

    private DetailedReportDto toDetailDto(PostureMetricView view, LlmResponseDto llm, Long reportId) {
        PostureMetric metric = view.metric();
        LlmResponseDto.PerMetric llmPart = llm.findByType(metric.type()).orElse(null);
        return DetailedReportDto.builder()
                .reportId(reportId)
                .type(metric.type())
                .status(view.status())
                .measured(view.measured())
                .stdVal(metric.stdVal())
                .refMin(metric.refMin())
                .refMax(metric.refMax())
                .sensitivity(metric.sensitivity())
                .problem(llmPart == null ? null : llmPart.problem())
                .summary(llmPart == null ? null : llmPart.summary())
                .improved(llmPart == null ? null : llmPart.improved())
                .build();
    }
}
