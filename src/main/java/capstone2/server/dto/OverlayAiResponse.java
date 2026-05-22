package capstone2.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI → Spring 오버레이 응답 바디. {@code { "overlay_s3_url": "<오버레이 영상 S3 key>" }}
 * <p>{@code overlay_s3_url}은 full URL이 아니라 순수 S3 key다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OverlayAiResponse(@JsonProperty("overlay_s3_url") String overlayS3Url) {
}
