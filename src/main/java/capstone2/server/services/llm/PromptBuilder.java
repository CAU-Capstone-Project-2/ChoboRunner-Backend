package capstone2.server.services.llm;

import capstone2.server.dto.PoseAnalysisInput;
import capstone2.server.services.posture.PostureMetric;
import capstone2.server.services.posture.PostureMetricView;
import capstone2.server.services.rag.RagChunk;
import capstone2.server.services.rag.RagContext;

import java.util.List;

public final class PromptBuilder {

    public static final String SYSTEM_PROMPT = """
            역할: 당신은 친근한 러닝 자세 코치다. 측면 영상 분석 결과를 바탕으로
            자세 개선을 제안한다. 모든 출력은 한국어로 작성한다.

            [필수 톤]
            - "진단", "치료", "처방", "환자", "증상", "질환" 등 의료 용어 사용 금지
            - 문장 유형별 톤 분리:
              · 측정 결과 서술 → 단정형 ("흔들림이 크지 않습니다")
              · 행동 권고 → 구체 지시 ("보폭을 약간 짧게 가져가보세요")
              · 재촬영 안내 → 단정형 ("측면 전신이 보이도록 다시 촬영해주세요")
              · 사용자 자세 평가성 문장 → 제안형·격려 톤
            - "당신의 자세는 잘못되었습니다" 같은 단정적 평가 금지
            - 격려하는 톤 유지

            [절대 규칙]
            - 제공된 AI 피드백 문장(aiFeedback)을 코치 톤으로 자연스럽게 다시 풀어 설명한다
            - status는 AI/Rule이 이미 정한 값이므로 다시 판정하지 않는다
            - reasonCodes가 가리키는 측정 한계를 반영하되, 데이터에 없는 부위(허리, 발목 등)는 추측하지 않는다
            - 행동 권고는 측정된 세 지표(trunk_lean, knee_flexion, foot_strike_pattern)에 한해서만\s
              구체적으로 제시한다. 보폭·케이던스·수직 진동·좌우 대칭·발 회내 등 측정되지 않은 영역은 권고하지 않는다.
            - 측정 신뢰도에 따라 톤 강도 차등:
              · 신뢰도 높음 → 측정 결과 단정 + 구체 행동 권고
              · 신뢰도 중간 → 측정 결과 단정 + 신중한 권고
              · lowConfidence=true → "측면 전신이 잘 보이도록 다시 촬영하면 더 정확한 분석이 가능합니다" 톤으로 안내

            [참고 지침 활용]
            - "[참고 지침]" 섹션이 있으면 출처로만 활용하고 본문을 그대로 옮기지 말고 코치 톤으로 재구성한다
            - 참고 지침이 비어 있으면 일반적인 코치 톤 권고로 응답한다
            - 참고 지침에 의학적 표현이 섞여 있어도 진단/처방 톤으로 재서술하지 말고 운동 자세 관점으로만 활용한다
            - 참고 지침에 데이터가 없는 부위는 여전히 추측 금지

            [출력 형식]
            지정된 JSON 스키마로만 응답한다. 마크다운, 백틱, 추가 설명 금지.
            """;

    private static final int MAX_CHUNK_PREVIEW_CHARS = 1000;

    private PromptBuilder() {
    }

    public static String buildUserPrompt(List<PostureMetricView> views, PoseAnalysisInput input) {
        return buildUserPrompt(views, input, RagContext.empty());
    }

    public static String buildUserPrompt(List<PostureMetricView> views,
                                         PoseAnalysisInput input,
                                         RagContext ragContext) {
        boolean lowConfidence = "low_confidence".equals(input.status());
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 러닝 측면 영상 자세 분석 결과다.\n\n");

        sb.append("[전체 상태]\n");
        sb.append("- status: ").append(nullToDash(input.status())).append('\n');
        sb.append("- lowConfidence: ").append(lowConfidence).append('\n');
        sb.append("- reasonCodes: ").append(joinReasonCodes(input.reasonCodes())).append('\n');
        if (input.message() != null && !input.message().isBlank()) {
            sb.append("- 안내 메시지: ").append(input.message()).append('\n');
        }

        sb.append("\n[지표별 결과]\n");
        for (PostureMetricView v : views) {
            sb.append("- ").append(v.metric().type()).append(":\n");
            sb.append("  measured: ").append(blankToDash(v.measured()))
                    .append(isFootStrike(v) ? "" : "°").append('\n');
            sb.append("  status: ").append(nullToDash(v.status())).append('\n');
            sb.append("  aiFeedback: ").append(joinAiTexts(v.aiTexts())).append('\n');
        }

        appendRagSection(sb, views, ragContext);

        sb.append("""

                다음 JSON 스키마로만 응답하라:
                {
                  "perMetric": [
                    { "type": "<metric_type>", "summary": "<1~2문장 요약>", "improved": "<1~2문장 개선 제안>", "problem": "<짧은 문제 요약>" }
                  ],
                  "totalFeedback": "<2~3문장 종합 코멘트>"
                }
                """);
        return sb.toString();
    }

    private static void appendRagSection(StringBuilder sb,
                                         List<PostureMetricView> views,
                                         RagContext ragContext) {
        if (ragContext == null || ragContext.isEmpty()) {
            return;
        }
        sb.append("\n[참고 지침]\n");
        for (PostureMetricView view : views) {
            PostureMetric metric = view.metric();
            List<RagChunk> chunks = ragContext.chunksFor(metric);
            if (chunks.isEmpty()) continue;
            sb.append('(').append(metric.type()).append(")\n");
            for (RagChunk c : chunks) {
                sb.append("- ").append(truncate(c.text())).append('\n');
            }
        }
    }

    private static String truncate(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() <= MAX_CHUNK_PREVIEW_CHARS) return clean;
        return clean.substring(0, MAX_CHUNK_PREVIEW_CHARS) + "…";
    }

    private static boolean isFootStrike(PostureMetricView v) {
        return "foot_strike_pattern".equals(v.metric().type());
    }

    private static String joinReasonCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return "(없음)";
        return String.join(", ", codes);
    }

    private static String joinAiTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) return "(없음)";
        return String.join(" / ", texts);
    }

    private static String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    private static String blankToDash(String s) {
        return (s == null || s.isBlank()) ? "N/A" : s;
    }
}
