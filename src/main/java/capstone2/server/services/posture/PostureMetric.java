package capstone2.server.services.posture;

/**
 * 자세 분석 핵심 지표 3종과, AI 메시지 안에서 같은 지표가 위치별로 다르게 표기되는
 * 키들의 매핑 상수.
 *
 * <ul>
 *   <li>{@link #type} — canonical 명. {@code DetailedReport.type} 에 그대로 저장.</li>
 *   <li>{@link #feedbackKey} — {@code feedback_messages[].metric} 에서 쓰는 키.</li>
 *   <li>{@link #stdVal}/{@link #refMin}/{@link #refMax}/{@link #sensitivity} —
 *       score 지수감쇠 계산용 참조값. 임계치 자체는 AI가 관리하지만 점수 산출 기준값은
 *       백엔드 상수로 보존한다. foot_strike 는 참조 범위가 없다.</li>
 * </ul>
 */
public enum PostureMetric {

    TRUNK_LEAN("trunk_lean", "trunk_lean", "7.5", 0, 15, 2.0),
    INITIAL_KNEE_FLEXION("initial_knee_flexion", "knee_flexion", "25.0", 15, 35, 2.0),
    FOOT_STRIKE_PATTERN("foot_strike_pattern", "foot_strike", "0", 0, 0, 0.0);

    private final String type;
    private final String feedbackKey;
    private final String stdVal;
    private final int refMin;
    private final int refMax;
    private final double sensitivity;

    PostureMetric(String type, String feedbackKey, String stdVal,
                  int refMin, int refMax, double sensitivity) {
        this.type = type;
        this.feedbackKey = feedbackKey;
        this.stdVal = stdVal;
        this.refMin = refMin;
        this.refMax = refMax;
        this.sensitivity = sensitivity;
    }

    public String type() {
        return type;
    }

    public String feedbackKey() {
        return feedbackKey;
    }

    public String stdVal() {
        return stdVal;
    }

    public int refMin() {
        return refMin;
    }

    public int refMax() {
        return refMax;
    }

    public double sensitivity() {
        return sensitivity;
    }
}
