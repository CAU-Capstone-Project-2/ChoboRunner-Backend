package capstone2.server.services.llm;

import capstone2.server.dto.LlmResponseDto;
import capstone2.server.dto.PoseAnalysisInput;
import capstone2.server.services.posture.PostureMetricView;
import capstone2.server.services.rag.RagContext;
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

@Component
public class PostureLlmClient {

    private static final Logger log = LoggerFactory.getLogger(PostureLlmClient.class);
    private static final String OPENAI_BASE_URL = "https://api.openai.com";
    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String model;
    private final double temperature;
    private final Duration timeout;
    private final boolean enabled;

    public PostureLlmClient(@Value("${spring.ai.openai.api-key:}") String apiKey,
                            @Value("${spring.ai.openai.chat.options.model:gpt-5.5}") String model,
                            @Value("${spring.ai.openai.chat.options.temperature:1.0}") double temperature,
                            @Value("${posture.llm.timeout-seconds:30}") long timeoutSeconds) {
        String trimmedKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = !trimmedKey.isEmpty() && !trimmedKey.startsWith("${");
        this.webClient = WebClient.builder()
                .baseUrl(OPENAI_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + trimmedKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.model = model;
        this.temperature = temperature;
        this.timeout = Duration.ofSeconds(timeoutSeconds);

        if (enabled) {
            log.info("PostureLlmClient initialized: enabled=true, model={}, key={}, keyLength={}, timeoutSeconds={}",
                    model, maskKey(trimmedKey), trimmedKey.length(), timeoutSeconds);
        } else {
            log.warn("PostureLlmClient initialized: enabled=false, key={}. LLM 호출은 fallback 메시지로 대체됩니다.",
                    maskKey(trimmedKey));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    static String maskKey(String key) {
        if (key == null) return "<null>";
        String trimmed = key.trim();
        if (trimmed.isEmpty()) return "<empty>";
        if (trimmed.startsWith("${")) return "<unresolved-placeholder>";
        if (trimmed.length() <= 8) return "***";
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    public LlmResponseDto generate(List<PostureMetricView> views, PoseAnalysisInput input) {
        return generate(views, input, RagContext.empty());
    }

    public LlmResponseDto generate(List<PostureMetricView> views, PoseAnalysisInput input,
                                   RagContext ragContext) {
        if (!enabled) {
            log.warn("OpenAI API key is not configured. Falling back to default messages.");
            return null;
        }

        String userPrompt = PromptBuilder.buildUserPrompt(views, input, ragContext);

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", PromptBuilder.SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            String rawBody = webClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();

            if (rawBody == null || rawBody.isBlank()) {
                log.warn("LLM returned empty body");
                return null;
            }

            JsonNode response = mapper.readTree(rawBody);
            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                log.warn("LLM returned empty content");
                return null;
            }
            return parseLenient(content);
        } catch (Exception e) {
            log.warn("LLM call failed, falling back to defaults", e);
            return null;
        }
    }

    private String extractContent(JsonNode response) {
        if (response == null) return null;
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return null;
        return choices.get(0).path("message").path("content").asText(null);
    }

    private LlmResponseDto parseLenient(String content) {
        try {
            return mapper.readValue(content, LlmResponseDto.class);
        } catch (Exception first) {
            String cleaned = stripCodeFence(content);
            try {
                return mapper.readValue(cleaned, LlmResponseDto.class);
            } catch (Exception second) {
                log.warn("Failed to parse LLM JSON output: {}", content, second);
                return null;
            }
        }
    }

    private String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) t = t.substring(firstNewline + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }
}
