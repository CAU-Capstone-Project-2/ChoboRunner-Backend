package capstone2.server.dto;

/**
 * Spring → AI 오버레이 요청 바디. {@code { "key": "<원본 영상 S3 key>" }}
 */
public record OverlayAiRequest(String key) {
}
