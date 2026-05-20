package capstone2.server.services.posture;

import java.util.List;

/**
 * {@code analysis_result} 한 건에서 추출한 metric별 중간 표현.
 * Rule 판정이 없는 v3 구조에서 LLM 입력·영속화를 잇는 단일 모델이다.
 *
 * @param metric   지표 종류
 * @param measured {@code DetailedReport.measured} 에 저장할 문자열 (각도 값, 없으면 빈 문자열)
 * @param status   {@code DetailedReport.status}. trunk/knee 는 "주의"/"정상",
 *                 foot_strike 는 착지 패턴(RFS/MFS/FFS)
 * @param aiTexts  해당 metric에 대한 AI feedback_messages 의 display_text 목록 (LLM 입력용)
 */
public record PostureMetricView(
        PostureMetric metric,
        String measured,
        String status,
        List<String> aiTexts
) {
}
