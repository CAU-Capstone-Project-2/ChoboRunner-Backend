package capstone2.server.services.llm;

import capstone2.server.dto.LlmResponseDto;
import capstone2.server.services.posture.PostureMetric;
import capstone2.server.services.posture.PostureMetricView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmOutputSanitizerTest {

    private final LlmOutputSanitizer sanitizer = new LlmOutputSanitizer();

    @Test
    void usesFallbackWhenLlmResponseIsNull() {
        List<PostureMetricView> views = List.of(
                view(PostureMetric.TRUNK_LEAN, "정상"),
                view(PostureMetric.INITIAL_KNEE_FLEXION, "주의")
        );

        LlmResponseDto result = sanitizer.sanitize(null, views, false);

        assertThat(result.perMetric()).hasSize(2);
        assertThat(result.perMetric().get(0).type()).isEqualTo("trunk_lean");
        assertThat(result.perMetric().get(0).summary())
                .isEqualTo(FallbackMessages.summaryOf("trunk_lean", "정상"));
        assertThat(result.perMetric().get(0).improved())
                .isEqualTo(FallbackMessages.IMPROVED_DEFAULT);
        assertThat(result.perMetric().get(0).problem())
                .isEqualTo(FallbackMessages.problemOf("trunk_lean", "정상"));
        assertThat(result.totalFeedback())
                .isEqualTo(FallbackMessages.TOTAL_FEEDBACK_DEFAULT);
    }

    @Test
    void replacesForbiddenTermsWithFallback() {
        var dto = new LlmResponseDto(
                List.of(new LlmResponseDto.PerMetric(
                        "trunk_lean",
                        "이 자세는 무릎 질환의 위험을 보여줍니다.",
                        "스트레칭을 처방합니다.",
                        "증상이 의심됩니다.")),
                "전반적인 진단을 제공합니다."
        );

        LlmResponseDto result = sanitizer.sanitize(dto, List.of(view(PostureMetric.TRUNK_LEAN, "정상")), false);

        assertThat(result.perMetric().get(0).summary())
                .isEqualTo(FallbackMessages.summaryOf("trunk_lean", "정상"));
        assertThat(result.perMetric().get(0).improved())
                .isEqualTo(FallbackMessages.IMPROVED_DEFAULT);
        assertThat(result.perMetric().get(0).problem())
                .isEqualTo(FallbackMessages.problemOf("trunk_lean", "정상"));
        assertThat(result.totalFeedback())
                .isEqualTo(FallbackMessages.TOTAL_FEEDBACK_DEFAULT);
    }

    @Test
    void trimsTooLongStrings() {
        String longSummary = "안".repeat(250);
        String longTotal = "전".repeat(500);
        var dto = new LlmResponseDto(
                List.of(new LlmResponseDto.PerMetric("trunk_lean", longSummary, "짧은 제안", "짧은 문제")),
                longTotal
        );

        LlmResponseDto result = sanitizer.sanitize(dto, List.of(view(PostureMetric.TRUNK_LEAN, "정상")), false);

        assertThat(result.perMetric().get(0).summary()).hasSize(200);
        assertThat(result.perMetric().get(0).improved()).isEqualTo("짧은 제안");
        assertThat(result.perMetric().get(0).problem()).isEqualTo("짧은 문제");
        assertThat(result.totalFeedback()).hasSize(400);
    }

    @Test
    void preservesValidLlmContent() {
        var dto = new LlmResponseDto(
                List.of(new LlmResponseDto.PerMetric(
                        "trunk_lean",
                        "안정적인 상체 기울기가 관찰됩니다.",
                        "현재 자세를 유지하면 좋겠습니다.",
                        "두드러진 문제는 없습니다.")),
                "전반적으로 좋은 자세가 관찰됩니다."
        );

        LlmResponseDto result = sanitizer.sanitize(dto, List.of(view(PostureMetric.TRUNK_LEAN, "정상")), false);

        assertThat(result.perMetric().get(0).summary())
                .isEqualTo("안정적인 상체 기울기가 관찰됩니다.");
        assertThat(result.perMetric().get(0).improved())
                .isEqualTo("현재 자세를 유지하면 좋겠습니다.");
        assertThat(result.perMetric().get(0).problem())
                .isEqualTo("두드러진 문제는 없습니다.");
        assertThat(result.totalFeedback())
                .isEqualTo("전반적으로 좋은 자세가 관찰됩니다.");
    }

    @Test
    void buildsEntryForEachViewEvenWhenLlmOmits() {
        var dto = new LlmResponseDto(
                List.of(new LlmResponseDto.PerMetric(
                        "trunk_lean", "안정적입니다.", "유지해보세요.", "문제 없음")),
                "OK"
        );

        LlmResponseDto result = sanitizer.sanitize(dto, List.of(
                view(PostureMetric.TRUNK_LEAN, "정상"),
                view(PostureMetric.INITIAL_KNEE_FLEXION, "주의")), false);

        assertThat(result.perMetric()).hasSize(2);
        assertThat(result.perMetric().get(1).type()).isEqualTo("initial_knee_flexion");
        assertThat(result.perMetric().get(1).summary())
                .isEqualTo(FallbackMessages.summaryOf("initial_knee_flexion", "주의"));
    }

    @Test
    void lowConfidenceFallsBackToLowConfidenceMessages() {
        LlmResponseDto result = sanitizer.sanitize(null,
                List.of(view(PostureMetric.TRUNK_LEAN, "정상")), true);

        assertThat(result.perMetric().get(0).summary())
                .isEqualTo(FallbackMessages.LOW_CONFIDENCE_SUMMARY);
        assertThat(result.perMetric().get(0).improved())
                .isEqualTo(FallbackMessages.LOW_CONFIDENCE_IMPROVED);
        assertThat(result.totalFeedback())
                .isEqualTo(FallbackMessages.LOW_CONFIDENCE_TOTAL_FEEDBACK);
    }

    private PostureMetricView view(PostureMetric metric, String status) {
        return new PostureMetricView(metric, "10.0", status, List.of());
    }
}
