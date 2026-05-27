package capstone2.server.services.llm;

import capstone2.server.dto.PoseAnalysisInput;
import capstone2.server.services.posture.PostureMetric;
import capstone2.server.services.posture.PostureMetricView;
import capstone2.server.services.rag.RagChunk;
import capstone2.server.services.rag.RagContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    @Test
    void omitsRagSectionWhenContextEmpty() {
        String prompt = PromptBuilder.buildUserPrompt(views(), input(), RagContext.empty());
        assertThat(prompt).doesNotContain("[참고 지침]");
    }

    @Test
    void omitsRagSectionForBackwardCompatibleOverload() {
        String prompt = PromptBuilder.buildUserPrompt(views(), input());
        assertThat(prompt).doesNotContain("[참고 지침]");
    }

    @Test
    void appendsRagSectionWithMetricGrouping() {
        RagChunk c1 = chunk("c1", "상체가 앞으로 너무 기울면 코어 활성화가 필요하다.");
        RagChunk c2 = chunk("c2", "착지 시 무릎이 굽혀지면 충격 흡수가 향상된다.");
        RagContext ctx = new RagContext(Map.of(
                PostureMetric.TRUNK_LEAN, List.of(c1),
                PostureMetric.INITIAL_KNEE_FLEXION, List.of(c2)
        ));

        String prompt = PromptBuilder.buildUserPrompt(views(), input(), ctx);

        assertThat(prompt).contains("[참고 지침]");
        assertThat(prompt).contains("(trunk_lean)");
        assertThat(prompt).contains("코어 활성화");
        assertThat(prompt).contains("(initial_knee_flexion)");
        assertThat(prompt).contains("충격 흡수");
    }

    @Test
    void normalizesWhitespaceAndTrimsLongChunks() {
        String longText = " 한국어\n   참고 지침 ".repeat(200);
        RagChunk c = chunk("long", longText);
        RagContext ctx = new RagContext(Map.of(PostureMetric.TRUNK_LEAN, List.of(c)));

        String prompt = PromptBuilder.buildUserPrompt(views(), input(), ctx);

        // chunk 본문이 한 줄(공백 정규화)로 들어가고 길이 제한이 적용된다.
        int start = prompt.indexOf("(trunk_lean)\n- ") + "(trunk_lean)\n- ".length();
        int end = prompt.indexOf('\n', start);
        String chunkLine = prompt.substring(start, end);
        assertThat(chunkLine).endsWith("…");
        assertThat(chunkLine).doesNotContain("\n");
        assertThat(chunkLine).doesNotContain("   "); // 연속 공백 없음
    }

    @Test
    void systemPromptIncludesRagGuard() {
        assertThat(PromptBuilder.SYSTEM_PROMPT).contains("[참고 지침 활용]");
        assertThat(PromptBuilder.SYSTEM_PROMPT).contains("운동 자세 관점으로만 활용");
    }

    private static List<PostureMetricView> views() {
        return List.of(
                new PostureMetricView(PostureMetric.TRUNK_LEAN, "7.8", "주의", List.of()),
                new PostureMetricView(PostureMetric.INITIAL_KNEE_FLEXION, "19.4", "주의", List.of()),
                new PostureMetricView(PostureMetric.FOOT_STRIKE_PATTERN, "6.2", "RFS", List.of())
        );
    }

    private static PoseAnalysisInput input() {
        return new PoseAnalysisInput(
                "analysis_result", "success", null, "left",
                null, null, null,
                null, List.of(), null, List.of()
        );
    }

    private static RagChunk chunk(String id, String text) {
        return new RagChunk(id, 0.5, text, "trunk_lean", "coaching", "t2",
                "title", "doi", "url");
    }
}
