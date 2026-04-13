package capstone2.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "S3 업로드 결과")
public record S3UploadResultDto(
        @Schema(description = "S3 객체 키", example = "uploads/2026/04/sample.png")
        String key,
        @Schema(description = "업로드에 사용된 S3 URL", example = "https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/2026/04/sample.png")
        String uploadUrl,
        @Schema(description = "브라우저에서 접근 가능한 조회 URL", example = "https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/2026/04/sample.png")
        String viewUrl
) {
}

