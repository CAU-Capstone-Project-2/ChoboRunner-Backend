package capstone2.server.services.llm;

import capstone2.server.domain.MetricEvaluation;
import capstone2.server.domain.Verdict;
import capstone2.server.dto.LlmResponseDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmOutputSanitizerTest {

    private final LlmOutputSanitizer sanitizer = new LlmOutputSanitizer();

    @Test
    void usesFallbackWhenLlmResponseIsNull() {
        List<MetricEvaluation> evals = List.of(
                eval("trunk_lean", Verdict.OPTIMAL),
                eval("initial_knee_flexion", Verdict.UNRELIABLE)
        );

        LlmResponseDto result = sanitizer.sanitize(null, evals);

        assertThat(result.perMetric()).hasSize(2);
        assertThat(result.perMetric().get(0).type()).isEqualTo("trunk_lean");
        assertThat(result.perMetric().get(0).summary())
                .isEqualTo("권장 영역의 forward lean 자세가 관찰됩니다.");
        assertThat(result.perMetric().get(0).improved())
                .isEqualTo(FallbackMessages.IMPROVED_DEFAULT);
        assertThat(result.totalFeedback())
                .isEqualTo(FallbackMessages.TOTAL_FEEDBACK_DEFAULT);
    }

    @Test
    void replacesForbiddenTermsWithFallback() {
        var dto = new LlmResponseDto(
                List.of(new LlmResponseDto.PerMetric(
                        "trunk_lean",
                        "이 자세는 무릎 질환의 위험을 보여줍니다.",
                        "스트레칭을 처방합니다.")),
                "전반적인 진단을 제공합니다."
        );

        LlmResponseDto result = sanitizer.sanitize(dto, List.of(eval("trunk_lean", Verdict.OPTIMAL)));

        assertThat(result.perMetric().get(0).summary())
                .isEqualTo("권장 영역의 forward lean 자세가 관찰됩니다.");
        assertThat(result.perMetric().get(0).improved())
                .isEqualTo(FallbackMessages.IMPROVED_DEFAULT);
        assertThat(result.totalFeedback())
                .isEqualTo(FallbackMessages.TOTAL_FEEDBACK_DEFAULT);
    }

    @Test
    void trimsTooLongStrings() {
        String longSummary = "안".repeat(250);
        String longTotal = "전".repeat(500);
        var dto = new LlmResponseDto(
                List.of(new LlmResponseDto.PerMetric("trunk_lean", longSummary, "짧은 제안")),
                longTotal
        );

        LlmResponseDto result = sanitizer.sanitize(dto, List.of(eval("trunk_lean", Verdict.OPTIMAL)));

        assertThat(result.perMetric().get(0).summary()).hasSize(200);
        assertThat(result.perMetric().get(0).improved()).isEqualTo("짧은 제안");
        assertThat(result.totalFeedback()).hasSize(400);
    }

    @Test
    void preservesValidLlmContent() {
        var dto = new LlmResponseDto(
                List.of(new LlmResponseDto.PerMetric(
                        "trunk_lean",
                        "안정적인 forward lean이 관찰됩니다.",
                        "현재 자세를 유지하면 좋겠습니다.")),
                "전반적으로 좋은 자세가 관찰됩니다."
        );

        LlmResponseDto result = sanitizer.sanitize(dto, List.of(eval("trunk_lean", Verdict.OPTIMAL)));

        assertThat(result.perMetric().get(0).summary())
                .isEqualTo("안정적인 forward lean이 관찰됩니다.");
        assertThat(result.perMetric().get(0).improved())
                .isEqualTo("현재 자세를 유지하면 좋겠습니다.");
        assertThat(result.totalFeedback())
                .isEqualTo("전반적으로 좋은 자세가 관찰됩니다.");
    }

    @Test
    void buildsEntryForEachEvalEvenWhenLlmOmits() {
        var dto = new LlmResponseDto(
                List.of(new LlmResponseDto.PerMetric(
                        "trunk_lean", "안정적입니다.", "유지해보세요.")),
                "OK"
        );

        LlmResponseDto result = sanitizer.sanitize(dto, List.of(
                eval("trunk_lean", Verdict.OPTIMAL),
                eval("initial_knee_flexion", Verdict.HIGH_RISK)));

        assertThat(result.perMetric()).hasSize(2);
        assertThat(result.perMetric().get(1).type()).isEqualTo("initial_knee_flexion");
        assertThat(result.perMetric().get(1).summary())
                .isEqualTo("무릎이 거의 펴진 상태로 착지하는 경향이 보입니다.");
    }

    private MetricEvaluation eval(String type, Verdict v) {
        return MetricEvaluation.builder()
                .metricName(type)
                .verdict(v)
                .status(v.name())
                .skipped(false)
                .build();
    }
}
