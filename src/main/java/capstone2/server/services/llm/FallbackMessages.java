package capstone2.server.services.llm;

/**
 * LLM 호출/응답 실패 시 사용하는 기본 문구. v3는 Verdict가 없으므로
 * (metric type + status) 조합으로 키를 잡는다. status는 "정상"/"주의" 또는
 * foot_strike의 착지 패턴(RFS/MFS/FFS).
 */
public final class FallbackMessages {

    public static final String IMPROVED_DEFAULT =
            "측면 전신이 잘 보이도록 다시 촬영하면 더 정확한 제안이 가능합니다.";

    public static final String PROBLEM_DEFAULT = "측정값을 참고해 자세를 점검해보세요.";

    public static final String TOTAL_FEEDBACK_DEFAULT =
            "전반적인 자세를 확인했습니다. 측면 전신이 잘 보이도록 촬영하면 더 정확한 분석이 가능합니다.";

    /** status == low_confidence 일 때 사용하는 공통 문구. */
    public static final String LOW_CONFIDENCE_SUMMARY =
            "측정 신뢰도가 낮아 단정하기 어렵습니다.";

    public static final String LOW_CONFIDENCE_IMPROVED =
            "측면 전신이 흔들림 없이 잘 보이도록 다시 촬영하면 더 정확한 분석이 가능합니다.";

    public static final String LOW_CONFIDENCE_TOTAL_FEEDBACK =
            "측정 신뢰도가 낮은 편입니다. 측면 전신이 잘 보이도록 다시 촬영하면 더 정확한 분석이 가능합니다.";

    private FallbackMessages() {
    }

    /** metric별 summary 기본 문구. */
    public static String summaryOf(String type, String status) {
        boolean warning = "주의".equals(status);
        return switch (type == null ? "" : type) {
            case "trunk_lean" -> warning
                    ? "상체 기울기에서 개선해볼 만한 점이 보입니다."
                    : "상체 기울기는 권장 범위에서 관찰됩니다.";
            case "initial_knee_flexion" -> warning
                    ? "착지 시 무릎 굴곡에서 개선해볼 만한 점이 보입니다."
                    : "착지 시 무릎 굴곡은 안정적인 범위에서 관찰됩니다.";
            case "foot_strike_pattern" -> "현재 " + status + " 착지 패턴이 관찰됩니다.";
            default -> "참고 지표로 관찰된 패턴입니다.";
        };
    }

    /** metric별 problem 기본 문구. */
    public static String problemOf(String type, String status) {
        if (!"주의".equals(status)) {
            return "특별히 두드러지는 문제는 관찰되지 않았습니다.";
        }
        return switch (type == null ? "" : type) {
            case "trunk_lean" -> "상체 기울기가 권장 범위를 벗어난 편입니다.";
            case "initial_knee_flexion" -> "착지 시 무릎 굴곡이 권장 범위를 벗어난 편입니다.";
            default -> PROBLEM_DEFAULT;
        };
    }
}
