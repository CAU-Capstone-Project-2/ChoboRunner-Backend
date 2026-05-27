package capstone2.server.services.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI /v1/embeddings WebClient. 단일 텍스트 → float[].
 * RAG 비활성/실패 시 null 반환.
 */
@Component
public class PostureEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(PostureEmbeddingClient.class);
    private static final String OPENAI_BASE_URL = "https://api.openai.com";
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String model;
    private final Duration timeout;
    private final boolean enabled;

    public PostureEmbeddingClient(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${posture.rag.embedding-model:text-embedding-3-large}") String model,
            @Value("${posture.rag.timeout-seconds:10}") long timeoutSeconds) {
        String trimmedKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = !trimmedKey.isEmpty() && !trimmedKey.startsWith("${");
        this.webClient = WebClient.builder()
                .baseUrl(OPENAI_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + trimmedKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);

        if (enabled) {
            log.info("PostureEmbeddingClient initialized: enabled=true, model={}, timeoutSeconds={}",
                    model, timeoutSeconds);
        } else {
            log.warn("PostureEmbeddingClient initialized: enabled=false. Embedding 호출은 비활성화됩니다.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 단일 텍스트 임베딩. 실패 시 null. */
    public float[] embed(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return null;
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "input", List.of(text)
        );
        try {
            String raw = webClient.post()
                    .uri(EMBEDDINGS_PATH)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();
            if (raw == null || raw.isBlank()) {
                log.warn("Embedding response empty");
                return null;
            }
            JsonNode root = mapper.readTree(raw);
            JsonNode embedding = root.path("data").path(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                log.warn("Embedding payload missing");
                return null;
            }
            float[] vec = new float[embedding.size()];
            for (int i = 0; i < vec.length; i++) {
                vec[i] = (float) embedding.get(i).asDouble();
            }
            return vec;
        } catch (Exception e) {
            log.warn("Embedding call failed", e);
            return null;
        }
    }
}
