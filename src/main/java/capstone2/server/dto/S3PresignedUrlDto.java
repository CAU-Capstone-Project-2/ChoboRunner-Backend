package capstone2.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "S3 presigned URL 발급 결과")
public record S3PresignedUrlDto(
        @Schema(description = "S3 객체 키", example = "running_2026_05_22.mp4")
        String key,
        @Schema(description = "기간 한정 접근 가능한 presigned GET URL")
        String url,
        @Schema(description = "URL 유효 기간(초)", example = "3600")
        long expiresInSeconds
) {
}
