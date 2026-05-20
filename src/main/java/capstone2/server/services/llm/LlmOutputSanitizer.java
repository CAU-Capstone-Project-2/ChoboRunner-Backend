package capstone2.server.services.llm;

import capstone2.server.dto.LlmResponseDto;
import capstone2.server.services.posture.PostureMetricView;
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

    /**
     * LLM 원응답을 검증·보정한다. metric 누락/금칙어/길이 초과 시 fallback 으로 대체하며,
     * 입력 {@code views} 순서대로 perMetric 을 채워 반환한다.
     */
    public LlmResponseDto sanitize(LlmResponseDto raw, List<PostureMetricView> views, boolean lowConfidence) {
        List<LlmResponseDto.PerMetric> resultMetrics = new ArrayList<>();
        for (PostureMetricView view : views) {
            LlmResponseDto.PerMetric llm = (raw == null)
                    ? null
                    : raw.findByType(view.metric().type()).orElse(null);
            resultMetrics.add(buildSafeMetric(view, llm, lowConfidence));
        }

        String totalFeedback = (raw == null) ? null : raw.totalFeedback();
        totalFeedback = sanitizeText(totalFeedback, MAX_TOTAL_FEEDBACK_LEN,
                lowConfidence ? FallbackMessages.LOW_CONFIDENCE_TOTAL_FEEDBACK
                              : FallbackMessages.TOTAL_FEEDBACK_DEFAULT);

        return new LlmResponseDto(resultMetrics, totalFeedback);
    }

    private LlmResponseDto.PerMetric buildSafeMetric(PostureMetricView view,
                                                     LlmResponseDto.PerMetric llm,
                                                     boolean lowConfidence) {
        String type = view.metric().type();
        String status = view.status();

        String summary = sanitizeText(
                llm == null ? null : llm.summary(),
                MAX_PER_METRIC_LEN,
                lowConfidence ? FallbackMessages.LOW_CONFIDENCE_SUMMARY
                              : FallbackMessages.summaryOf(type, status));

        String improved = sanitizeText(
                llm == null ? null : llm.improved(),
                MAX_PER_METRIC_LEN,
                lowConfidence ? FallbackMessages.LOW_CONFIDENCE_IMPROVED
                              : FallbackMessages.IMPROVED_DEFAULT);

        String problem = sanitizeText(
                llm == null ? null : llm.problem(),
                MAX_PER_METRIC_LEN,
                FallbackMessages.problemOf(type, status));

        return new LlmResponseDto.PerMetric(type, summary, improved, problem);
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
