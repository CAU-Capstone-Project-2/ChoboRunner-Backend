package capstone2.server.controllers;

import capstone2.server.services.VideoOverlayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.Callable;

@RestController
@RequestMapping("/api/analyze")
@RequiredArgsConstructor
@Tag(name = "Video Overlay", description = "영상 오버레이 합성 API")
public class VideoOverlayController {

    private final VideoOverlayService overlayService;

    /**
     * 원본 영상을 업로드하면 AI 오버레이 합성을 거쳐 {@code RunSession.videoS3Key}를 갱신하고
     * 갱신된 {@code RunSessionDto}를 반환한다.
     * <p>합성 완료까지 응답을 대기하는 비동기 요청 처리({@code Callable}) 방식이라
     * 서블릿 컨테이너 스레드는 대기 동안 점유되지 않는다.
     */
    @Operation(
            summary = "영상 오버레이 요청",
            description = "RunSessionId는 RequestParam으로, 원본 영상은 multipart/form-data로 업로드한다. "
                    + "AI 오버레이 합성이 끝날 때까지 응답을 대기한다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = VideoOverlayRequest.class)
                    )
            )
    )
    @PostMapping(value = "/overlay", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Callable<ResponseEntity<?>> overlay(
            @Parameter(description = "오버레이 영상을 연결할 RunSession id", required = true)
            @RequestParam("RunSessionId") Long runSessionId,
            @RequestBody MultipartFile video) {
        if (video == null || video.isEmpty()) {
            return () -> ResponseEntity.badRequest().body(Map.of("message", "업로드할 영상이 없습니다."));
        }
        return () -> overlayService.process(runSessionId, video);
    }

    @Schema(name = "VideoOverlayRequest", description = "영상 오버레이 요청 본문 (원본 영상)")
    private static class VideoOverlayRequest {
        @Schema(description = "Android가 촬영한 원본 영상", type = "string", format = "binary",
                requiredMode = Schema.RequiredMode.REQUIRED)
        public MultipartFile video;
    }
}
