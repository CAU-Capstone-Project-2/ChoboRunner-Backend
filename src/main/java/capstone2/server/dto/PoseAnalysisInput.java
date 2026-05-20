package capstone2.server.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 서버가 WebSocket으로 보내는 {@code analysis_result} 메시지 매핑 DTO.
 *
 * <p>snake_case 매핑은 역직렬화 시점의 ObjectMapper(PropertyNamingStrategies.SNAKE_CASE)에
 * 위임한다. 미사용 필드는 무시한다(FAIL_ON_UNKNOWN_PROPERTIES=false).
 *
 * <p>{@code status == "failed"} 인 경우 metrics / metricDetails / analysisSide /
 * qualitySummary / feedbackMessages 가 모두 null 로 온다.
 */
public record PoseAnalysisInput(
        String type,
        String status,
        VideoMeta videoMeta,
        String analysisSide,
        Metrics metrics,
        Map<String, MetricDetail> metricDetails,
        QualitySummary qualitySummary,
        String primaryReasonCode,
        List<String> reasonCodes,
        String message,
        List<FeedbackMessage> feedbackMessages
) {

    public record VideoMeta(
            Double durationSec,
            Double fpsActual,
            Resolution resolution,
            Integer totalFrames
    ) {
    }

    public record Resolution(Integer width, Integer height) {
    }

    public record Metrics(
            String footStrikePattern,
            Double footStrikeAngleDeg,
            Double initialKneeFlexionDeg,
            Double trunkLeanDeg
    ) {
    }

    public record MetricDetail(
            Double median,
            List<Double> iqr,
            Integer nStrides
    ) {
    }

    public record QualitySummary(
            Double validFrameRatio,
            Integer icCandidateCount,
            Integer validStrideCount,
            Double landmarkVisibilityAvg,
            String targetTrackingStability
    ) {
    }

    public record FeedbackMessage(
            String category,
            String metric,
            String ttsText,
            String displayText,
            Integer priority,
            Boolean ttsEnabled,
            Boolean confidencePrefix
    ) {
    }
}
