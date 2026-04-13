package capstone2.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "S3 객체 링크 정보")
public record S3ObjectLinkDto(
        @Schema(description = "S3 객체 키", example = "uploads/2026/04/sample.png")
        String key,
        @Schema(description = "객체 크기(바이트)", example = "204800")
        Long size,
        @Schema(description = "객체 최종 수정 시각(UTC)")
        Instant lastModified,
        @Schema(description = "브라우저에서 접근 가능한 조회 URL", example = "https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/2026/04/sample.png")
        String viewUrl
) {
}

