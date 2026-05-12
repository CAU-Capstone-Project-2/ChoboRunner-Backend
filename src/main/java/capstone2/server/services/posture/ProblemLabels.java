package capstone2.server.services.posture;

import capstone2.server.domain.Verdict;

public final class ProblemLabels {

    private ProblemLabels() {
    }

    public static String of(String type, Verdict verdict) {
        if (type == null || verdict == null) return null;
        return switch (type) {
            case "trunk_lean" -> switch (verdict) {
                case OPTIMAL    -> "권장 영역";
                case CAUTION    -> "수직 또는 약한 forward lean";
                case SUBOPTIMAL -> "과도한 forward lean";
                case HIGH_RISK  -> "위험 자세";
                case UNRELIABLE -> "측정 신뢰도 낮음";
            };
            case "initial_knee_flexion" -> switch (verdict) {
                case OPTIMAL    -> "권장 굴곡 범위";
                case HIGH_RISK  -> "Stiff knee (하드 랜딩)";
                case CAUTION    -> "굴곡 경계 영역";
                case SUBOPTIMAL -> "Excessive flexion";
                case UNRELIABLE -> "측정 신뢰도 낮음";
            };
            default -> null;
        };
    }
}
