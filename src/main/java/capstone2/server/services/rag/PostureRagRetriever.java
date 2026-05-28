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

    private final PostureEmbeddingClient embeddingClient;
    private final PineconeClient pineconeClient;
    private final PostureHyDEGenerator hydeGenerator;

    @Value("${posture.rag.enabled:false}")
    private boolean enabled;

    @Value("${posture.rag.top-k:4}")
    private int topK;

    @Value("${posture.rag.hyde-enabled:false}")
    private boolean hydeEnabled;

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
            String query = resolveEmbeddingInput(view);
            log.debug("RAG query metric={} text={}", view.metric().type(), query);
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

    /**
     * 임베딩 입력 결정. hyde-enabled 이면 HyDE 패시지 우선, 실패/비활성 시 자연어 쿼리로 fallback.
     */
    private String resolveEmbeddingInput(PostureMetricView view) {
        if (hydeEnabled && hydeGenerator != null && hydeGenerator.isEnabled()) {
            String hyde = hydeGenerator.generate(view);
            if (hyde != null && !hyde.isBlank()) {
                log.debug("RAG: HyDE 패시지 사용 metric={}, length={}", view.metric().type(), hyde.length());
                return hyde;
            }
            log.debug("RAG: HyDE 생성 실패 → 자연어 쿼리로 fallback metric={}", view.metric().type());
        }
        return buildQuery(view);
    }

    /**
     * 자연어 영어 쿼리. T2 코퍼스 본문이 영어 OA 논문 위주이므로 한국어 키-값 메타 대신
     * passage 와 같은 언어/문체의 짧은 진술문을 임베딩한다.
     */
    private String buildQuery(PostureMetricView view) {
        String measured = view.measured() == null ? "" : view.measured().trim();
        return switch (view.metric()) {
            case TRUNK_LEAN -> buildTrunkLeanQuery(measured);
            case INITIAL_KNEE_FLEXION -> buildKneeFlexionQuery(measured);
            case FOOT_STRIKE_PATTERN -> buildFootStrikeQuery(view.status());
        };
    }

    private String buildTrunkLeanQuery(String measured) {
        StringBuilder sb = new StringBuilder(
                "A distance runner shows excessive forward trunk lean during running");
        if (!measured.isEmpty()) {
            sb.append(" with a measured trunk lean angle of ").append(measured).append(" degrees");
        }
        sb.append(". Discuss the biomechanical effects of forward trunk inclination on running")
          .append(" economy and injury risk, and provide coaching cues to correct posture.");
        return sb.toString();
    }

    private String buildKneeFlexionQuery(String measured) {
        StringBuilder sb = new StringBuilder(
                "A distance runner shows insufficient knee flexion at initial foot contact while running");
        if (!measured.isEmpty()) {
            sb.append(" (knee flexion angle of ").append(measured).append(" degrees at landing)");
        }
        sb.append(". Explain how limited knee flexion at initial contact affects impact loading")
          .append(" and provide coaching cues to increase knee flexion during the landing phase.");
        return sb.toString();
    }

    private String buildFootStrikeQuery(String pattern) {
        String descriptor = footStrikeDescriptor(pattern);
        return "A distance runner exhibits a " + descriptor
                + " during running. Describe the biomechanical implications of a " + descriptor
                + " on impact forces, running economy and injury risk, and recommended technique"
                + " adjustments or coaching cues.";
    }

    private String footStrikeDescriptor(String pattern) {
        if (pattern == null) return "particular foot strike pattern";
        return switch (pattern.trim().toUpperCase()) {
            case "RFS" -> "rearfoot strike (heel strike) pattern";
            case "MFS" -> "midfoot strike pattern";
            case "FFS" -> "forefoot strike pattern";
            default -> pattern + " foot strike pattern";
        };
    }

    /**
     * metadata filter: category=metric.type OR category=general.
     * tone(coaching/clinical) 필터는 제거 — 휴리스틱이 과도하게 clinical 분류해서
     * 실제 코칭에 유용한 러닝 바이오메카닉 본문(injury/pain 단어 포함) 다수가 누락됨.
     */
    private Map<String, Object> buildFilter(PostureMetric metric) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("category", Map.of("$in", List.of(metric.type(), GENERAL_CATEGORY)));
        return filter;
    }
}
