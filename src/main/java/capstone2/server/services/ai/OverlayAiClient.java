package capstone2.server.services.ai;

import capstone2.server.dto.OverlayAiRequest;
import capstone2.server.dto.OverlayAiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * AI FastAPI 오버레이 엔드포인트(/analyze/overlay) 호출 클라이언트.
 * {@code PostureLlmClient}와 동일한 WebClient 패턴.
 */
@Component
public class OverlayAiClient {

    private static final Logger log = LoggerFactory.getLogger(OverlayAiClient.class);

    private final WebClient webClient;
    private final Duration timeout;

    public OverlayAiClient(@Value("${ai-server.overlay-url}") String overlayUrl,
                           @Value("${posture.overlay.timeout-seconds:600}") long timeoutSeconds) {
        this.webClient = WebClient.builder()
                .baseUrl(overlayUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        log.info("OverlayAiClient initialized: overlayUrl={}, timeoutSeconds={}", overlayUrl, timeoutSeconds);
    }

    /**
     * AI 서버에 오버레이 합성을 요청하고, 합성된 영상의 S3 key를 반환한다.
     * 호출 실패·타임아웃 시 예외를 던진다. 호출부(VideoOverlayService)가 잡아 500으로 매핑한다.
     *
     * @param originalKey 원본 영상의 S3 key
     * @return 오버레이 영상 S3 key (응답에 누락 시 {@code null})
     */
    public String requestOverlay(String originalKey) {
        OverlayAiResponse response = webClient.post()
                .bodyValue(new OverlayAiRequest(originalKey))
                .retrieve()
                .bodyToMono(OverlayAiResponse.class)
                .timeout(timeout)
                .block();
        return response == null ? null : response.overlayS3Url();
    }
}
