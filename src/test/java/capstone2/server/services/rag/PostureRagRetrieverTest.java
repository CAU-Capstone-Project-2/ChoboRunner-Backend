package capstone2.server.services.rag;

import capstone2.server.dto.PoseAnalysisInput;
import capstone2.server.services.posture.PostureMetric;
import capstone2.server.services.posture.PostureMetricView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostureRagRetrieverTest {

    private PostureEmbeddingClient embeddingClient;
    private PineconeClient pineconeClient;
    private PostureRagRetriever retriever;

    @BeforeEach
    void setUp() {
        embeddingClient = mock(PostureEmbeddingClient.class);
        pineconeClient = mock(PineconeClient.class);
        when(embeddingClient.isEnabled()).thenReturn(true);
        when(pineconeClient.isEnabled()).thenReturn(true);
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        retriever = new PostureRagRetriever(embeddingClient, pineconeClient);
        ReflectionTestUtils.setField(retriever, "enabled", true);
        ReflectionTestUtils.setField(retriever, "topK", 4);
    }

    @Test
    void disabledFlagReturnsEmpty() {
        ReflectionTestUtils.setField(retriever, "enabled", false);
        RagContext ctx = retriever.retrieve(List.of(view(PostureMetric.TRUNK_LEAN, "주의")), input());
        assertThat(ctx.isEmpty()).isTrue();
        verify(embeddingClient, never()).embed(anyString());
        verify(pineconeClient, never()).query(any(), anyInt(), any());
    }

    @Test
    void skipsMetricsWithStatusNormal() {
        when(pineconeClient.query(any(), anyInt(), any()))
                .thenReturn(List.of(chunk("c1", "본문")));

        RagContext ctx = retriever.retrieve(List.of(
                view(PostureMetric.TRUNK_LEAN, "정상"),
                view(PostureMetric.INITIAL_KNEE_FLEXION, "주의")
        ), input());

        // 정상 metric은 skip → 1회만 검색
        verify(embeddingClient, times(1)).embed(anyString());
        verify(pineconeClient, times(1)).query(any(), anyInt(), any());
        assertThat(ctx.chunksFor(PostureMetric.TRUNK_LEAN)).isEmpty();
        assertThat(ctx.chunksFor(PostureMetric.INITIAL_KNEE_FLEXION)).hasSize(1);
    }

    @Test
    void footStrikeIsAlwaysQueriedWhenStatusPresent() {
        when(pineconeClient.query(any(), anyInt(), any()))
                .thenReturn(List.of(chunk("fs1", "foot")));

        RagContext ctx = retriever.retrieve(
                List.of(view(PostureMetric.FOOT_STRIKE_PATTERN, "RFS")), input());

        verify(embeddingClient, times(1)).embed(anyString());
        assertThat(ctx.chunksFor(PostureMetric.FOOT_STRIKE_PATTERN)).hasSize(1);
    }

    @Test
    void dedupesChunkIdsAcrossMetrics() {
        when(pineconeClient.query(any(), anyInt(), any()))
                .thenReturn(List.of(chunk("shared", "공통 본문"), chunk("a", "고유 1")))
                .thenReturn(List.of(chunk("shared", "공통 본문"), chunk("b", "고유 2")));

        RagContext ctx = retriever.retrieve(List.of(
                view(PostureMetric.TRUNK_LEAN, "주의"),
                view(PostureMetric.INITIAL_KNEE_FLEXION, "주의")
        ), input());

        List<RagChunk> trunk = ctx.chunksFor(PostureMetric.TRUNK_LEAN);
        List<RagChunk> knee = ctx.chunksFor(PostureMetric.INITIAL_KNEE_FLEXION);
        assertThat(trunk).extracting(RagChunk::id).containsExactly("shared", "a");
        assertThat(knee).extracting(RagChunk::id).containsExactly("b");
    }

    @Test
    void appliesCategoryFilterOnly() {
        when(pineconeClient.query(any(), anyInt(), any())).thenReturn(List.of());

        retriever.retrieve(List.of(view(PostureMetric.TRUNK_LEAN, "주의")), input());

        ArgumentCaptor<Map<String, Object>> filterCap = ArgumentCaptor.forClass(Map.class);
        verify(pineconeClient).query(any(), anyInt(), filterCap.capture());
        Map<String, Object> filter = filterCap.getValue();
        assertThat(filter).doesNotContainKey("tone");
        assertThat(filter).containsEntry("category",
                Map.of("$in", List.of("trunk_lean", "general")));
    }

    @Test
    void embeddingFailureSkipsThatMetric() {
        when(embeddingClient.embed(anyString())).thenReturn(null);

        RagContext ctx = retriever.retrieve(
                List.of(view(PostureMetric.TRUNK_LEAN, "주의")), input());

        assertThat(ctx.isEmpty()).isTrue();
        verify(pineconeClient, never()).query(any(), anyInt(), any());
    }

    private static PostureMetricView view(PostureMetric metric, String status) {
        return new PostureMetricView(metric, "10.0", status, List.of("AI 피드백 예시"));
    }

    private static PoseAnalysisInput input() {
        return new PoseAnalysisInput(
                "analysis_result", "success", null, "left",
                null, null, null,
                "primary", List.of("rc1"), "msg", List.of()
        );
    }

    private static RagChunk chunk(String id, String text) {
        return new RagChunk(id, 0.5, text, "trunk_lean", "coaching", "t2",
                "title", "doi", "url");
    }
}
