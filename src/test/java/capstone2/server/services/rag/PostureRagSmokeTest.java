package capstone2.server.services.rag;

import capstone2.server.dto.PoseAnalysisInput;
import capstone2.server.services.posture.PostureMetric;
import capstone2.server.services.posture.PostureMetricView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring 측 RAG 종단 스모크 테스트.
 *
 * <p>실제 OpenAI embeddings + Pinecone query 를 호출하므로 다음 환경변수가 모두 있어야 실행된다:
 * <ul>
 *   <li>{@code POSTURE_RAG_IT=true} — 가드</li>
 *   <li>{@code OPENAI_API_KEY}</li>
 *   <li>{@code PINECONE_API_KEY}</li>
 *   <li>{@code PINECONE_INDEX_HOST}</li>
 * </ul>
 *
 * <p>실행 예 (PowerShell):
 * <pre>
 * $env:POSTURE_RAG_IT="true"
 * .\gradlew test --tests "capstone2.server.services.rag.PostureRagSmokeTest"
 * </pre>
 */
// RANDOM_PORT 로 실제 톰캣 띄움 — WebSocketConfig 의 ServerContainer 의존성 만족.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "rag-it"})
@EnabledIfEnvironmentVariable(named = "POSTURE_RAG_IT", matches = "true")
class PostureRagSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(PostureRagSmokeTest.class);

    @Autowired
    private PostureRagRetriever retriever;

    @Test
    void retrievesChunksForAttentionMetrics() {
        // trunk_lean = 주의, foot_strike = RFS, knee = 정상 (정상은 retrieve skip)
        List<PostureMetricView> views = List.of(
                new PostureMetricView(PostureMetric.TRUNK_LEAN, "9.5", "주의",
                        List.of("상체가 앞으로 너무 기울어졌어요")),
                new PostureMetricView(PostureMetric.INITIAL_KNEE_FLEXION, "18.0", "정상", List.of()),
                new PostureMetricView(PostureMetric.FOOT_STRIKE_PATTERN, "6.0", "RFS",
                        List.of("발 뒷꿈치 착지 패턴이 관찰됩니다"))
        );
        PoseAnalysisInput input = new PoseAnalysisInput(
                "analysis_result", "success", null, "left",
                null, null, null,
                "primary_reason", List.of("rc_trunk"), null, List.of()
        );

        RagContext ctx = retriever.retrieve(views, input);

        logContext(ctx);

        assertThat(ctx.isEmpty()).as("RAG 컨텍스트가 비어있지 않아야 함").isFalse();

        // trunk_lean (주의) → 검색됨
        List<RagChunk> trunk = ctx.chunksFor(PostureMetric.TRUNK_LEAN);
        assertThat(trunk).as("trunk_lean 검색 결과").isNotEmpty();
        assertThat(trunk.get(0).text()).as("trunk_lean 첫 chunk 본문").isNotBlank();
        assertThat(trunk).extracting(RagChunk::category)
                .as("trunk_lean category 는 trunk_lean 또는 general")
                .allMatch(c -> "trunk_lean".equals(c) || "general".equals(c));

        // foot_strike (RFS) → 검색됨 (foot_strike 는 status 있으면 항상 검색)
        List<RagChunk> foot = ctx.chunksFor(PostureMetric.FOOT_STRIKE_PATTERN);
        assertThat(foot).as("foot_strike 검색 결과").isNotEmpty();
        assertThat(foot).extracting(RagChunk::category)
                .as("foot_strike category 는 foot_strike_pattern 또는 general")
                .allMatch(c -> "foot_strike_pattern".equals(c) || "general".equals(c));

        // initial_knee_flexion (정상) → 검색 스킵
        assertThat(ctx.chunksFor(PostureMetric.INITIAL_KNEE_FLEXION))
                .as("정상 metric 은 retrieve 스킵")
                .isEmpty();
    }

    @Test
    void retrievesChunksForKneeFlexionAttention() {
        // initial_knee_flexion 단독 "주의" 시나리오. 다른 metric 은 "정상"/skip.
        List<PostureMetricView> views = List.of(
                new PostureMetricView(PostureMetric.TRUNK_LEAN, "6.0", "정상", List.of()),
                new PostureMetricView(PostureMetric.INITIAL_KNEE_FLEXION, "12.0", "주의",
                        List.of("착지 시 무릎 굽힘이 부족합니다")),
                new PostureMetricView(PostureMetric.FOOT_STRIKE_PATTERN, "", "", List.of())
        );
        PoseAnalysisInput input = new PoseAnalysisInput(
                "analysis_result", "success", null, "left",
                null, null, null,
                "primary_reason", List.of("rc_knee"), null, List.of()
        );

        RagContext ctx = retriever.retrieve(views, input);
        logContext(ctx);

        List<RagChunk> knee = ctx.chunksFor(PostureMetric.INITIAL_KNEE_FLEXION);
        assertThat(knee).as("initial_knee_flexion 검색 결과").isNotEmpty();
        assertThat(knee).extracting(RagChunk::category)
                .as("knee category 는 initial_knee_flexion 또는 general")
                .allMatch(c -> "initial_knee_flexion".equals(c) || "general".equals(c));
    }

    private void logContext(RagContext ctx) {
        log.info("=== RAG smoke result ===");
        ctx.byMetric().forEach((metric, chunks) -> {
            log.info("  metric={} hits={}", metric.type(), chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                RagChunk c = chunks.get(i);
                String preview = c.text().replaceAll("\\s+", " ");
                if (preview.length() > 140) preview = preview.substring(0, 140) + "…";
                log.info("    [{}] score={} category={} tone={} title={}",
                        i + 1, String.format("%.3f", c.score()), c.category(), c.tone(), c.title());
                log.info("        {}", preview);
            }
        });
    }
}
