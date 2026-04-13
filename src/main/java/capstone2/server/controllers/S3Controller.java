package capstone2.server.controllers;

import capstone2.server.dto.S3ObjectLinkDto;
import capstone2.server.dto.S3UploadResultDto;
import capstone2.server.services.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/s3")
@RequiredArgsConstructor
@Tag(name = "S3", description = "S3 파일 업로드, 다운로드 및 링크 조회 API")
public class S3Controller {

    private final S3Service s3Service;

    @Operation(
            summary = "파일 업로드",
            description = "multipart/form-data로 파일 1개를 업로드하고 업로드/조회 링크를 반환합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "multipart/form-data",
                            schema = @Schema(implementation = UploadFileRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "업로드 성공",
                    content = @Content(schema = @Schema(implementation = S3UploadResultDto.class))),
            @ApiResponse(responseCode = "400", description = "파일 누락 또는 빈 파일"),
            @ApiResponse(responseCode = "500", description = "업로드 처리 실패")
    })
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @Parameter(description = "업로드할 파일", required = true,
                    content = @Content(mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary")))
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "업로드할 파일이 없습니다."));
        }

        try {
            S3UploadResultDto result = s3Service.uploadWithViewLink(file);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "파일 업로드에 실패했습니다.", "detail", e.getMessage()));
        }
    }

    @Operation(summary = "업로드된 파일 링크 목록 조회", description = "S3 객체 목록과 조회 가능한 링크를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = S3ObjectLinkDto.class))))
    @GetMapping("/links")
    public ResponseEntity<List<S3ObjectLinkDto>> listLinks() {
        return ResponseEntity.ok(s3Service.listObjectLinks());
    }

    @Operation(summary = "S3 key로 파일 다운로드", description = "S3 객체 key를 전달하면 파일을 바이너리로 반환합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "다운로드 성공",
                    content = @Content(mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "key 누락 또는 잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "해당 key의 객체를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "다운로드 처리 실패")
    })
    @GetMapping("/download")
    public ResponseEntity<?> download(
            @Parameter(description = "다운로드할 S3 객체 key", required = true)
            @RequestParam("key") String key) {
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "다운로드할 S3 key가 없습니다."));
        }

        try {
            S3Service.DownloadResult result = s3Service.download(key);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(result.fileName(), StandardCharsets.UTF_8)
                    .build());
            headers.setContentType(resolveMediaType(result.contentType()));
            headers.setContentLength(result.content().length);

            return new ResponseEntity<>(result.content(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (S3Exception e) {
            String errorCode = e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode();
            String errorMessage = e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage();

            if (e.statusCode() == 404 || "NoSuchKey".equalsIgnoreCase(errorCode)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "해당 S3 객체를 찾을 수 없습니다.", "key", key));
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "S3 다운로드 처리 중 오류가 발생했습니다.", "detail", errorMessage));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "파일 다운로드에 실패했습니다.", "detail", e.getMessage()));
        }
    }

    @Schema(name = "S3UploadRequest", description = "S3 파일 업로드 요청 본문")
    private static class UploadFileRequest {
        @Schema(description = "업로드할 파일", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
        public MultipartFile file;
    }

    private MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

