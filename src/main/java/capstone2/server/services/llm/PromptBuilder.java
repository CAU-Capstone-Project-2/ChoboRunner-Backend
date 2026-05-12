package capstone2.server.services.llm;

import capstone2.server.domain.MetricEvaluation;
import capstone2.server.dto.PoseAnalysisInput;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PromptBuilder {

    public static final String SYSTEM_PROMPT = """
            역할: 당신은 친근한 러닝 자세 코치다. 측면 영상 분석 데이터를 바탕으로
            자세 개선을 제안한다. 모든 출력은 한국어로 작성한다.

            [필수 톤]
            - "진단", "치료", "처방", "환자", "증상", "질환" 등 의료 용어 사용 금지
            - "~할 수 있을 것 같아요", "~해보면 어떨까요" 같은 제안형 어미 사용
            - "당신의 자세는 잘못되었습니다" 같은 단정적 평가 금지
            - 격려하는 톤 유지

            [절대 규칙]
            - verdict를 직접 판정하지 말 것 (Rule이 이미 판정함)
            - 입력으로 받은 verdict를 신뢰하고 자연어화만 수행
            - 데이터에 없는 부위(허리, 발목 등)에 대해 추측 금지
            - UNRELIABLE 지표는 단정 대신 "측면 전신이 잘 보이도록 다시 촬영하면 더
              정확한 분석이 가능합니다"로 안내

            [출력 형식]
            지정된 JSON 스키마로만 응답한다. 마크다운, 백틱, 추가 설명 금지.
            """;

    private PromptBuilder() {
    }

    public static String buildUserPrompt(List<MetricEvaluation> evals,
                                         List<PoseAnalysisInput.Metric> metrics) {
        Map<String, PoseAnalysisInput.Metric> byName = indexByName(metrics);
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 러닝 IC 시점의 자세 분석 결과다. AI 측에서 보정된 측정값이며 verdict는 Rule이 산정했다.\n\n");
        sb.append("[지표별 평가]\n");

        for (MetricEvaluation e : evals) {
            sb.append("- ").append(e.metricName()).append(":\n");
            switch (e.metricName()) {
                case "trunk_lean", "initial_knee_flexion" -> {
                    sb.append("  measured: ").append(formatValue(e.rawValue())).append("°\n");
                    sb.append("  verdict: ").append(e.status()).append("\n");
                    appendCategory(sb, byName.get(e.metricName()));
                }
                case "foot_strike_pattern" -> {
                    sb.append("  pattern: ").append(e.status()).append(" (experimental, 참고용)\n");
                    appendCategory(sb, byName.get(e.metricName()));
                }
                default -> { /* skip */ }
            }
        }

        sb.append("\n다음 JSON 스키마로만 응답하라:\n");
        sb.append("""
                {
                  "perMetric": [
                    { "type": "<metric_name>", "summary": "<1~2문장>", "improved": "<1~2문장>" }
                  ],
                  "totalFeedback": "<2~3문장>"
                }
                """);
        return sb.toString();
    }

    private static void appendCategory(StringBuilder sb, PoseAnalysisInput.Metric m) {
        if (m == null) return;
        if (m.category() != null && !m.category().isBlank()) {
            sb.append("  category: ").append(m.category()).append("\n");
        }
    }

    private static String formatValue(Double v) {
        if (v == null) return "N/A";
        return String.format("%.2f", v);
    }

    private static Map<String, PoseAnalysisInput.Metric> indexByName(List<PoseAnalysisInput.Metric> metrics) {
        if (metrics == null) return Map.of();
        return Optional.of(metrics).orElse(List.of()).stream()
                .filter(m -> m != null && m.metricName() != null)
                .collect(java.util.stream.Collectors.toMap(
                        PoseAnalysisInput.Metric::metricName,
                        m -> m,
                        (a, b) -> a));
    }
}
