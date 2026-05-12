package capstone2.server.services;

import capstone2.server.domain.MetricEvaluation;
import capstone2.server.dto.LlmResponseDto;
import capstone2.server.dto.PoseAnalysisInput;
import capstone2.server.dto.PostureAnalysisResponse;
import capstone2.server.dto.S3UploadResultDto;
import capstone2.server.services.ai.AiAnalysisClient;
import capstone2.server.services.llm.LlmOutputSanitizer;
import capstone2.server.services.llm.PostureLlmClient;
import capstone2.server.services.posture.PostureAnalysisPersister;
import capstone2.server.services.posture.PostureRules;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostureAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(PostureAnalysisService.class);

    private final S3Service s3Service;
    private final AiAnalysisClient aiClient;
    private final PostureRules rules;
    private final PostureLlmClient llmClient;
    private final LlmOutputSanitizer sanitizer;
    private final PostureAnalysisPersister persister;

    public PostureAnalysisResponse analyze(MultipartFile file, Long runSessionId) throws IOException {
        log.info("Posture analysis start: runSessionId={}, fileSize={}", runSessionId,
                file == null ? 0 : file.getSize());

        S3UploadResultDto uploaded = s3Service.uploadWithViewLink(file);
        log.info("Uploaded to S3: key={}", uploaded.key());

        PoseAnalysisInput aiResult = aiClient.requestAnalyze(uploaded.key());

        List<MetricEvaluation> evals = rules.evaluateAll(aiResult.metrics());
        log.info("Rule evaluation produced {} entries", evals.size());

        LlmResponseDto llmRaw = llmClient.generate(evals, aiResult.metrics());
        LlmResponseDto llmFinal = sanitizer.sanitize(llmRaw, evals);
        log.info("LLM response sanitized: totalFeedback length={}, perMetric count={}",
                llmFinal.totalFeedback() == null ? 0 : llmFinal.totalFeedback().length(),
                llmFinal.perMetric().size());
        return persister.persist(runSessionId, evals, llmFinal, aiResult.overlayS3Url());
    }
}
