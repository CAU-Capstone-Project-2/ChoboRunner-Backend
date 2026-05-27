package capstone2.server.services.rag;

import capstone2.server.services.posture.PostureMetric;

import java.util.List;
import java.util.Map;

/**
 * Retriever 가 LLM 프롬프트에 주입할 검색 결과 묶음.
 * metric 별 chunk 리스트로 구성한다. RAG 비활성/실패 시에는 {@link #empty()} 사용.
 */
public record RagContext(Map<PostureMetric, List<RagChunk>> byMetric) {

    private static final RagContext EMPTY = new RagContext(Map.of());

    public static RagContext empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        if (byMetric == null || byMetric.isEmpty()) return true;
        return byMetric.values().stream().allMatch(l -> l == null || l.isEmpty());
    }

    public List<RagChunk> chunksFor(PostureMetric metric) {
        if (byMetric == null) return List.of();
        return byMetric.getOrDefault(metric, List.of());
    }
}
