package capstone2.server.services.ai;

import capstone2.server.dto.PoseAnalysisInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisClient.class);
    private static final int MAX_IN_MEMORY_BYTES = 8 * 1024 * 1024;
    private static final int RAW_BODY_LOG_LIMIT = 4000;

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Duration timeout;

    public AiAnalysisClient(@Value("${ai-server.api-url}") String baseUrl,
                            @Value("${ai-server.analyze-timeout-seconds:600}") long timeoutSeconds) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        log.info("AiAnalysisClient initialized: baseUrl={}, timeoutSeconds={}", baseUrl, timeoutSeconds);
    }

    public PoseAnalysisInput requestAnalyze(String s3Key) {
        log.info("AI analyze request: key={}", s3Key);
        try {
            String rawBody = webClient.post()
                    .uri("/api/entire_video_analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("key", s3Key))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();

            if (rawBody == null || rawBody.isBlank()) {
                log.warn("AI server returned empty body for key={}", s3Key);
                return new PoseAnalysisInput(null, null, List.of());
            }

            log.info("AI server response (key={}, length={}): {}",
                    s3Key, rawBody.length(), truncate(rawBody, RAW_BODY_LOG_LIMIT));

            PoseAnalysisInput parsed = mapper.readValue(rawBody, PoseAnalysisInput.class);
            int metricsCount = parsed.metrics() == null ? 0 : parsed.metrics().size();
            log.info("AI response parsed: runId={}, overlayS3Url={}, metricsCount={}",
                    parsed.runId(), parsed.overlayS3Url(), metricsCount);

            if (metricsCount == 0) {
                log.warn("AI response has no metrics. DetailedReport will be empty. Raw body: {}",
                        truncate(rawBody, RAW_BODY_LOG_LIMIT));
            }
            if (parsed.overlayS3Url() == null || parsed.overlayS3Url().isBlank()) {
                log.warn("AI response has no overlay_s3_url. RunSession.videoS3Url will not be updated.");
            }

            return parsed;
        } catch (WebClientResponseException e) {
            log.error("AI server returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI 서버 오류: " + e.getStatusCode(), e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI server call failed for key={}", s3Key, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI 서버 호출 실패", e);
        }
    }

    static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...(truncated " + (s.length() - max) + " chars)";
    }
}
