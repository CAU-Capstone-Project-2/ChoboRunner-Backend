package capstone2.server.services.posture;

import capstone2.server.dto.LlmResponseDto;
import capstone2.server.dto.PoseAnalysisInput;
import capstone2.server.services.llm.LlmOutputSanitizer;
import capstone2.server.services.llm.PostureLlmClient;
import capstone2.server.services.rag.PostureRagRetriever;
import capstone2.server.services.rag.RagContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 서버 WebSocket 스트림에서 {@code analysis_result} 메시지를 탭하여
 * LLM 코멘트를 생성하고 Report/DetailedReport 로 영속화한다.
 *
 * <p>{@link capstone2.server.websocket.VideoRelayHandler}가 Python→Android 텍스트 릴레이
 * 지점에서 {@link #onMessage}를 호출한다. 파싱·LLM·영속화는 WS 릴레이를 막지 않도록
 * 단일 스레드 Executor 에서 수행한다.
 */
@Service
@RequiredArgsConstructor
public class PostureResultService {

    private static final Logger log = LoggerFactory.getLogger(PostureResultService.class);

    private static final String RESULT_TYPE = "analysis_result";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_LOW_CONFIDENCE = "low_confidence";
    private static final String WARNING_CATEGORY = "posture_warning";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "posture-result");
        t.setDaemon(true);
        return t;
    });

    private final PostureLlmClient llmClient;
    private final LlmOutputSanitizer sanitizer;
    private final PostureAnalysisPersister persister;
    private final PostureRagRetriever ragRetriever;

    /**
     * WebSocket 텍스트 메시지를 수신한다. {@code analysis_result} 가 아닌 메시지는 무시한다.
     */
    public void onMessage(Long runId, String payload) {
        if (runId == null || payload == null || payload.isEmpty()) {
            return;
        }
        // frame_inference / analysis_progress 등 대량 메시지를 worker 큐에 쌓지 않도록 1차 필터
        if (!payload.contains(RESULT_TYPE)) {
            return;
        }
        worker.execute(() -> handle(runId, payload));
    }

    private void handle(Long runId, String payload) {
        PoseAnalysisInput input;
        try {
            input = objectMapper.readValue(payload, PoseAnalysisInput.class);
        } catch (Exception e) {
            log.warn("analysis_result 파싱 실패: runId={}", runId, e);
            return;
        }

        if (!RESULT_TYPE.equals(input.type())) {
            return;
        }

        if (STATUS_FAILED.equals(input.status())) {
            log.info("analysis_result status=failed, runId={}, 영속화 생략. message={}",
                    runId, input.message());
            return;
        }
        if (input.metrics() == null) {
            log.warn("analysis_result 에 metrics 가 없습니다. runId={}, status={}", runId, input.status());
            return;
        }

        boolean lowConfidence = STATUS_LOW_CONFIDENCE.equals(input.status());
        List<PostureMetricView> views = buildViews(input);

        try {
            RagContext ragContext;
            try {
                ragContext = ragRetriever.retrieve(views, input);
            } catch (Exception e) {
                log.warn("RAG retrieve 실패, 컨텍스트 없이 진행: runId={}", runId, e);
                ragContext = RagContext.empty();
            }
            LlmResponseDto raw = llmClient.generate(views, input, ragContext);
            LlmResponseDto llm = sanitizer.sanitize(raw, views, lowConfidence);
            Long reportId = persister.persist(runId, views, llm);
            log.info("Posture analysis 완료: runId={}, reportId={}, status={}, ragHits={}",
                    runId, reportId, input.status(), ragHitCount(ragContext));
        } catch (Exception e) {
            log.error("Posture analysis 처리 실패: runId={}", runId, e);
        }
    }

    private List<PostureMetricView> buildViews(PoseAnalysisInput input) {
        PoseAnalysisInput.Metrics m = input.metrics();
        List<PoseAnalysisInput.FeedbackMessage> feedback = input.feedbackMessages();

        List<PostureMetricView> views = new ArrayList<>();
        views.add(view(PostureMetric.TRUNK_LEAN, m.trunkLeanDeg(), null, feedback));
        views.add(view(PostureMetric.INITIAL_KNEE_FLEXION, m.initialKneeFlexionDeg(), null, feedback));
        views.add(view(PostureMetric.FOOT_STRIKE_PATTERN, m.footStrikeAngleDeg(),
                m.footStrikePattern(), feedback));
        return views;
    }

    private PostureMetricView view(PostureMetric metric, Double value, String footPattern,
                                   List<PoseAnalysisInput.FeedbackMessage> feedback) {
        String measured = value == null ? "" : String.valueOf(value);
        List<String> aiTexts = new ArrayList<>();
        boolean hasWarning = false;

        if (feedback != null) {
            for (PoseAnalysisInput.FeedbackMessage fb : feedback) {
                if (fb == null || !metric.feedbackKey().equals(fb.metric())) {
                    continue;
                }
                if (fb.displayText() != null && !fb.displayText().isBlank()) {
                    aiTexts.add(fb.displayText());
                }
                if (WARNING_CATEGORY.equals(fb.category())) {
                    hasWarning = true;
                }
            }
        }

        String status;
        if (metric == PostureMetric.FOOT_STRIKE_PATTERN) {
            status = (footPattern == null || footPattern.isBlank()) ? "정상" : footPattern;
        } else {
            status = hasWarning ? "주의" : "정상";
        }
        return new PostureMetricView(metric, measured, status, aiTexts);
    }

    private int ragHitCount(RagContext ctx) {
        if (ctx == null || ctx.byMetric() == null) return 0;
        int n = 0;
        for (var list : ctx.byMetric().values()) {
            if (list != null) n += list.size();
        }
        return n;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdown();
    }
}
