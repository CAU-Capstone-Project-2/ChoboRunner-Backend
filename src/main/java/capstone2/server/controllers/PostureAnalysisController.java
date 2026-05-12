package capstone2.server.controllers;

import capstone2.server.dto.PostureAnalysisResponse;
import capstone2.server.services.PostureAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/posture")
@RequiredArgsConstructor
@Tag(name = "Posture Analysis", description = "측면 영상 기반 자세 분석 + LLM 리포트 생성 API")
public class PostureAnalysisController {

    private final PostureAnalysisService service;

    @Operation(
            summary = "자세 분석 + 리포트 생성",
            description = "영상 파일을 S3에 업로드하고 AI 서버에 분석을 요청한 뒤 LLM 코멘트를 생성해 Report/DetailedReport에 영속화합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "multipart/form-data",
                            schema = @Schema(implementation = AnalyzeRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "분석 성공",
                    content = @Content(schema = @Schema(implementation = PostureAnalysisResponse.class))),
            @ApiResponse(responseCode = "400", description = "파일 누락 또는 잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "RunSession을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "처리 실패"),
            @ApiResponse(responseCode = "502", description = "AI 서버 호출 실패")
    })
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyze(
            @Parameter(description = "측면 러닝 영상 파일", required = true,
                    content = @Content(mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary")))
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "분석 결과를 연결할 RunSession id", required = true)
            @RequestParam("runSessionId") Long runSessionId) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "업로드할 파일이 없습니다."));
        }
        if (runSessionId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "runSessionId가 없습니다."));
        }

        try {
            PostureAnalysisResponse result = service.analyze(file, runSessionId);
            return ResponseEntity.ok(result);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "파일 처리에 실패했습니다.", "detail", e.getMessage()));
        }
    }

    @Schema(name = "PostureAnalyzeRequest", description = "자세 분석 요청 본문")
    private static class AnalyzeRequest {
        @Schema(description = "측면 러닝 영상 파일", type = "string", format = "binary",
                requiredMode = Schema.RequiredMode.REQUIRED)
        public MultipartFile file;

        @Schema(description = "RunSession id", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        public Long runSessionId;
    }
}
