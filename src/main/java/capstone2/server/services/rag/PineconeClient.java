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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pinecone Query REST 호출 (Serverless 인덱스 host).
 *
 * <p>설계 §4.3 / §8 기준 인덱스 {@code capstone2-posture-analyze-rag}, namespace {@code v1}.
 * host/api-key 미설정이면 enabled=false 로 비활성, 호출은 빈 결과 반환.
 */
@Component
public class PineconeClient {

    private static final Logger log = LoggerFactory.getLogger(PineconeClient.class);
    private static final String QUERY_PATH = "/query";
    private static final String API_VERSION = "2025-04";

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String namespace;
    private final Duration timeout;
    private final boolean enabled;

    public PineconeClient(
            @Value("${pinecone.api-key:}") String apiKey,
            @Value("${pinecone.index-host:}") String indexHost,
            @Value("${pinecone.namespace:v1}") String namespace,
            @Value("${posture.rag.timeout-seconds:10}") long timeoutSeconds) {
        String key = apiKey == null ? "" : apiKey.trim();
        String host = indexHost == null ? "" : indexHost.trim();
        this.enabled = !key.isEmpty() && !key.startsWith("${")
                && !host.isEmpty() && !host.startsWith("${");
        this.namespace = namespace;
        this.timeout = Duration.ofSeconds(timeoutSeconds);

        if (enabled) {
            String baseUrl = host.startsWith("http") ? host : "https://" + host;
            this.webClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Api-Key", key)
                    .defaultHeader("X-Pinecone-API-Version", API_VERSION)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            log.info("PineconeClient initialized: enabled=true, host={}, namespace={}", host, namespace);
        } else {
            this.webClient = null;
            log.warn("PineconeClient initialized: enabled=false. RAG 검색은 빈 결과로 처리됩니다.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 벡터로 Pinecone 인덱스 검색.
     *
     * @param vector  쿼리 임베딩
     * @param topK    상위 K
     * @param filter  metadata filter (예: {tone: "coaching", category: "trunk_lean"}).
     *                값이 null 이면 필터 미적용.
     * @return 매칭된 chunk 목록. 실패/비활성 시 빈 리스트.
     */
    public List<RagChunk> query(float[] vector, int topK, Map<String, Object> filter) {
        if (!enabled || vector == null || vector.length == 0 || topK <= 0) {
            return List.of();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("vector", toBoxed(vector));
        body.put("topK", topK);
        body.put("namespace", namespace);
        body.put("includeMetadata", true);
        if (filter != null && !filter.isEmpty()) {
            body.put("filter", filter);
        }
        try {
            String raw = webClient.post()
                    .uri(QUERY_PATH)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return parseMatches(mapper.readTree(raw));
        } catch (Exception e) {
            log.warn("Pinecone query failed", e);
            return List.of();
        }
    }

    private List<RagChunk> parseMatches(JsonNode root) {
        JsonNode matches = root.path("matches");
        if (!matches.isArray() || matches.isEmpty()) {
            return List.of();
        }
        List<RagChunk> out = new ArrayList<>(matches.size());
        for (JsonNode m : matches) {
            JsonNode md = m.path("metadata");
            String text = md.path("text").asText("");
            if (text.isBlank()) continue;
            out.add(new RagChunk(
                    m.path("id").asText(""),
                    m.path("score").asDouble(0.0),
                    text,
                    md.path("category").asText(null),
                    md.path("tone").asText(null),
                    md.path("tier").asText(null),
                    md.path("title").asText(null),
                    md.path("doi").asText(null),
                    md.path("source_url").asText(null)
            ));
        }
        return out;
    }

    private static List<Float> toBoxed(float[] arr) {
        List<Float> out = new ArrayList<>(arr.length);
        for (float v : arr) out.add(v);
        return out;
    }
}
