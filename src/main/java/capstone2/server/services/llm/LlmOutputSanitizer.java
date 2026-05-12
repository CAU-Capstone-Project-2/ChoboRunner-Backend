package capstone2.server.services.llm;

import capstone2.server.domain.MetricEvaluation;
import capstone2.server.dto.LlmResponseDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class LlmOutputSanitizer {

    private static final Set<String> FORBIDDEN_TERMS =
            Set.of("진단", "처방", "치료", "환자", "증상", "질환");

    private static final int MAX_PER_METRIC_LEN = 200;
    private static final int MAX_TOTAL_FEEDBACK_LEN = 400;

    public LlmResponseDto sanitize(LlmResponseDto raw, List<MetricEvaluation> evals) {
        List<LlmResponseDto.PerMetric> resultMetrics = new ArrayList<>();
        for (MetricEvaluation eval : evals) {
            LlmResponseDto.PerMetric llm = (raw == null)
                    ? null
                    : raw.findByType(eval.metricName()).orElse(null);
            resultMetrics.add(buildSafeMetric(eval, llm));
        }

        String totalFeedback = (raw == null) ? null : raw.totalFeedback();
        totalFeedback = sanitizeText(totalFeedback, MAX_TOTAL_FEEDBACK_LEN,
                FallbackMessages.TOTAL_FEEDBACK_DEFAULT);

        return new LlmResponseDto(resultMetrics, totalFeedback);
    }

    private LlmResponseDto.PerMetric buildSafeMetric(MetricEvaluation eval,
                                                     LlmResponseDto.PerMetric llm) {
        String summary = sanitizeText(
                llm == null ? null : llm.summary(),
                MAX_PER_METRIC_LEN,
                FallbackMessages.summaryOf(eval.metricName(), eval.verdict()));

        String improved = sanitizeText(
                llm == null ? null : llm.improved(),
                MAX_PER_METRIC_LEN,
                FallbackMessages.IMPROVED_DEFAULT);

        return new LlmResponseDto.PerMetric(eval.metricName(), summary, improved);
    }

    private String sanitizeText(String text, int maxLen, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        if (containsForbidden(text)) return fallback;
        return trimTo(text.trim(), maxLen);
    }

    private boolean containsForbidden(String text) {
        for (String term : FORBIDDEN_TERMS) {
            if (text.contains(term)) return true;
        }
        return false;
    }

    private String trimTo(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max);
    }
}
