package capstone2.server.services.rag;

import capstone2.server.dto.PoseAnalysisInput;
import capstone2.server.services.posture.PostureMetric;
import capstone2.server.services.posture.PostureMetricView;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자세 분석 결과로부터 metric별 RAG 컨텍스트를 가져온다.
 *
 * <p>설계 §6.4: metric별 분리 쿼리. 단, "주의" 상태(혹은 foot_strike 의 비-MFS) metric만
 * 검색해 토큰/지연을 줄인다. {@code posture.rag.enabled=false} 또는 의존 컴포넌트 비활성 시
 * 즉시 {@link RagContext#empty()} 반환.
 */
@Component
@RequiredArgsConstructor
public class PostureRagRetriever {

    private static final Logger log = LoggerFactory.getLogger(PostureRagRetriever.class);

    private static final String GENERAL_CATEGORY = "general";
    private static final String COACHING_TONE = "coaching";

    private final PostureEmbeddingClient embeddingClient;
    private final PineconeClient pineconeClient;

    @Value("${posture.rag.enabled:false}")
    private boolean enabled;

    @Value("${posture.rag.top-k:4}")
    private int topK;

    /**
     * @param views v3 metric 뷰
     * @param input 분석 결과 입력(reasonCodes/lowConfidence 컨텍스트 보강용)
     * @return metric별 검색 결과. 비활성/실패 시 empty.
     */
    public RagContext retrieve(List<PostureMetricView> views, PoseAnalysisInput input) {
        if (!enabled || !embeddingClient.isEnabled() || !pineconeClient.isEnabled()) {
            return RagContext.empty();
        }
        if (views == null || views.isEmpty()) {
            return RagContext.empty();
        }

        Map<PostureMetric, List<RagChunk>> byMetric = new EnumMap<>(PostureMetric.class);
        Set<String> seenChunkIds = new LinkedHashSet<>();

        for (PostureMetricView view : views) {
            if (!needsRetrieval(view)) {
                continue;
            }
            String query = buildQuery(view, input);
            float[] vec = embeddingClient.embed(query);
            if (vec == null) {
                log.debug("RAG: embedding 실패, metric={}", view.metric().type());
                continue;
            }
            Map<String, Object> filter = buildFilter(view.metric());
            List<RagChunk> matches = pineconeClient.query(vec, topK, filter);
            if (matches.isEmpty()) {
                continue;
            }
            List<RagChunk> deduped = new ArrayList<>(matches.size());
            for (RagChunk c : matches) {
                if (seenChunkIds.add(c.id())) {
                    deduped.add(c);
                }
            }
            if (!deduped.isEmpty()) {
                byMetric.put(view.metric(), deduped);
            }
        }
        return byMetric.isEmpty() ? RagContext.empty() : new RagContext(byMetric);
    }

    /** "주의" 상태인 metric만 검색 (foot_strike 는 패턴이 결정되어 있으면 항상 검색). */
    private boolean needsRetrieval(PostureMetricView view) {
        if (view == null || view.metric() == null) return false;
        if (view.metric() == PostureMetric.FOOT_STRIKE_PATTERN) {
            return view.status() != null && !view.status().isBlank();
        }
        return "주의".equals(view.status());
    }

    private String buildQuery(PostureMetricView view, PoseAnalysisInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("러닝 자세: ").append(view.metric().type());
        if (view.measured() != null && !view.measured().isBlank()) {
            sb.append(", 측정값=").append(view.measured());
        }
        if (view.status() != null && !view.status().isBlank()) {
            sb.append(", 상태=").append(view.status());
        }
        if (input != null && input.reasonCodes() != null && !input.reasonCodes().isEmpty()) {
            sb.append(", reasonCodes=").append(String.join(",", input.reasonCodes()));
        }
        if (view.aiTexts() != null && !view.aiTexts().isEmpty()) {
            sb.append(". AI 피드백: ").append(String.join(" / ", view.aiTexts()));
        }
        sb.append(". 개선 가이드를 제시.");
        return sb.toString();
    }

    /**
     * metadata filter: tone=coaching + (category=metric.type OR category=general).
     * Pinecone metadata filter 문법: {@code {"$or":[{"category":{"$eq":"trunk_lean"}}, ...]}}.
     */
    private Map<String, Object> buildFilter(PostureMetric metric) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("tone", Map.of("$eq", COACHING_TONE));
        filter.put("category", Map.of("$in", List.of(metric.type(), GENERAL_CATEGORY)));
        return filter;
    }
}
