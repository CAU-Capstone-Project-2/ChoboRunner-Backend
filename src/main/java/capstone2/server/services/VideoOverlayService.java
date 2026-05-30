package capstone2.server.services;

import capstone2.server.dto.RunSessionDto;
import capstone2.server.entities.RunSession;
import capstone2.server.services.ai.OverlayAiClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

/**
 * 영상 오버레이 오케스트레이션.
 * 컨트롤러가 반환한 {@code Callable}의 비동기 워커 스레드에서 {@link #process} 가 실행된다.
 */
@Service
@RequiredArgsConstructor
public class VideoOverlayService {

    private static final Logger log = LoggerFactory.getLogger(VideoOverlayService.class);

    private final S3Service s3Service;
    private final OverlayAiClient overlayAiClient;
    private final RunningSessionService runningSessionService;

    /**
     * 원본 영상 S3 업로드 → AI 오버레이 호출 → {@code RunSession.videoS3Key} 갱신.
     * 단계별 실패는 트랜잭션 밖에서 처리하고, 적절한 에러 {@code ResponseEntity}를 반환한다.
     */
    public ResponseEntity<?> process(Long runSessionId, MultipartFile video) {
        // 1. RunSession 조회
        Optional<RunSession> session = runningSessionService.findById(runSessionId);
        if (session.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "RunSession을 찾을 수 없습니다.", "RunSessionId", runSessionId));
        }

        // 2. 원본 영상 S3 업로드 (key = 원본 파일명)
        String originalKey;
        try {
            originalKey = s3Service.uploadOriginalVideo(video);
        } catch (Exception e) {
            log.error("원본 영상 S3 업로드 실패: runSessionId={}", runSessionId, e);
            return ResponseEntity.status(500)
                    .body(Map.of("message", "원본 영상 업로드에 실패했습니다.",
                            "detail", String.valueOf(e.getMessage())));
        }

        // 3. AI 오버레이 호출
        String overlayKey;
        try {
            overlayKey = overlayAiClient.requestOverlay(originalKey);
        } catch (Exception e) {
            log.error("AI 오버레이 호출 실패: runSessionId={}, originalKey={}", runSessionId, originalKey, e);
            return ResponseEntity.status(500).body(Map.of("message", "AI Server Overlay Video Failed"));
        }
        if (overlayKey == null || overlayKey.isBlank()) {
            log.error("AI 오버레이 응답에 overlay_s3_url 누락: runSessionId={}", runSessionId);
            return ResponseEntity.status(500).body(Map.of("message", "AI Server Overlay Video Failed"));
        }

        // 4. RunSession.videoS3Key 갱신 (트랜잭션)
        //    처리 도중 run 이 삭제됐을 수 있다(클라이언트가 overlay 와 delete 를 동시에 보낸 경우).
        Optional<RunSessionDto> dto = runningSessionService.updateVideoS3Key(runSessionId, overlayKey);
        if (dto.isEmpty()) {
            log.warn("오버레이 저장 대상 RunSession 없음(처리 중 삭제 추정): runSessionId={}", runSessionId);
            return ResponseEntity.status(409)
                    .body(Map.of("message", "해당 분석 세션이 종료/삭제되어 오버레이 결과를 저장할 수 없습니다.",
                            "RunSessionId", runSessionId));
        }

        // 5. 성공 응답
        return ResponseEntity.ok(dto.get());
    }
}
