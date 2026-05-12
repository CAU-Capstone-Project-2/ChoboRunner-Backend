package capstone2.server.services.llm;

import capstone2.server.domain.Verdict;

import java.util.Map;

public final class FallbackMessages {

    public static final String IMPROVED_DEFAULT =
            "측면 전신이 잘 보이도록 다시 촬영하면 더 정확한 제안이 가능합니다.";

    public static final String TOTAL_FEEDBACK_DEFAULT =
            "전반적인 자세를 확인했습니다. 측면 전신이 잘 보이도록 촬영하면 더 정확한 분석이 가능합니다.";

    private static final Map<String, Map<Verdict, String>> SUMMARY = Map.of(
            "trunk_lean", Map.of(
                    Verdict.OPTIMAL,    "권장 영역의 forward lean 자세가 관찰됩니다.",
                    Verdict.CAUTION,    "수직에 가까운 자세가 관찰됩니다.",
                    Verdict.SUBOPTIMAL, "다소 큰 forward lean 경향이 관찰됩니다.",
                    Verdict.HIGH_RISK,  "상체 기울기가 위험 영역에 있습니다.",
                    Verdict.UNRELIABLE, "측정 신뢰도가 낮아 단정하기 어렵습니다."
            ),
            "initial_knee_flexion", Map.of(
                    Verdict.OPTIMAL,    "안정적인 굴곡 범위의 IC 자세가 관찰됩니다.",
                    Verdict.HIGH_RISK,  "무릎이 거의 펴진 상태로 착지하는 경향이 보입니다.",
                    Verdict.CAUTION,    "권장 굴곡 범위 경계에 있습니다.",
                    Verdict.SUBOPTIMAL, "굴곡이 다소 깊은 경향이 관찰됩니다.",
                    Verdict.UNRELIABLE, "측정 신뢰도가 낮아 단정하기 어렵습니다."
            ),
            "foot_strike_pattern", Map.of(
                    Verdict.UNRELIABLE, "착지 경향을 한 가지로 단정하기 어렵습니다."
            )
    );

    private FallbackMessages() {
    }

    public static String summaryOf(String type, Verdict verdict) {
        if (type == null) return IMPROVED_DEFAULT;
        Map<Verdict, String> byVerdict = SUMMARY.get(type);
        if (byVerdict == null) {
            return "참고 지표로 관찰된 패턴입니다.";
        }
        if (verdict == null) {
            return "참고 지표로 관찰된 패턴입니다.";
        }
        return byVerdict.getOrDefault(verdict, IMPROVED_DEFAULT);
    }
}
