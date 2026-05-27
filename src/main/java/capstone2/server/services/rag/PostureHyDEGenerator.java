package capstone2.server.services.rag;

import capstone2.server.services.posture.PostureMetric;
import capstone2.server.services.posture.PostureMetricView;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HyDE (Hypothetical Document Embeddings) 보조 생성기.
 *
 * <p>짧은 자연어 쿼리 대신 LLM 으로 "이상적인 학술 답변 패시지" 1단락을 생성해 그것을
 * 임베딩하면, passage 와 같은 길이/문체로 매칭되어 cosine 점수가 올라간다.
 *
 * <p>실패 시 null 반환 — 호출 측에서 자연어 쿼리로 fallback 한다.
 */
@Component
public class PostureHyDEGenerator {

    private static final Logger log = LoggerFactory.getLogger(PostureHyDEGenerator.class);
    private static final String OPENAI_BASE_URL = "https://api.openai.com";
    private static final String CHAT_PATH = "/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            You are a sports biomechanics researcher. Write ONE paragraph (150-220 words)
            of clinical, factual prose about the given running-biomechanics topic, in the
            style of an academic paper abstract or discussion section. Use precise
            terminology (e.g., ground reaction force, vertical loading rate, eccentric
            quadriceps work, sagittal-plane kinematics). Cover mechanism, downstream
            biomechanical effects on impact loading / running economy / injury risk, and
            evidence-based corrective approaches. Do NOT use coaching tone, second-person
            address, encouragement, or markdown. Output the paragraph only, no preamble.
            """;

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String model;
    private final String reasoningEffort;
    private final Duration timeout;
    private final boolean enabled;

    public PostureHyDEGenerator(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${posture.rag.hyde-model:gpt-5.4-mini-2026-03-17}") String model,
            @Value("${posture.rag.hyde-reasoning-effort:none}") String reasoningEffort,
            @Value("${posture.rag.hyde-timeout-seconds:15}") long timeoutSeconds) {
        String trimmedKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = !trimmedKey.isEmpty() && !trimmedKey.startsWith("${");
        this.webClient = WebClient.builder()
                .baseUrl(OPENAI_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + trimmedKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.model = model;
        this.reasoningEffort = (reasoningEffort == null || reasoningEffort.isBlank()) ? null : reasoningEffort.trim();
        this.timeout = Duration.ofSeconds(timeoutSeconds);

        if (enabled) {
            log.info("PostureHyDEGenerator initialized: enabled=true, model={}, reasoningEffort={}",
                    model, this.reasoningEffort);
        } else {
            log.warn("PostureHyDEGenerator initialized: enabled=false (API key missing).");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * metric 뷰로부터 hypothetical 패시지 생성. 실패 시 null.
     */
    public String generate(PostureMetricView view) {
        if (!enabled || view == null || view.metric() == null) return null;
        String userPrompt = buildUserPrompt(view);

        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)
        ));
        if (reasoningEffort != null) {
            body.put("reasoning_effort", reasoningEffort);
        }
        // 패시지 ≤ 250 token 가정.
        body.put("max_completion_tokens", 400);

        try {
            String raw = webClient.post()
                    .uri(CHAT_PATH)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();
            if (raw == null || raw.isBlank()) return null;
            JsonNode root = mapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                log.warn("HyDE empty content: metric={}", view.metric().type());
                return null;
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("HyDE 생성 실패 metric={}", view.metric().type(), e);
            return null;
        }
    }

    private String buildUserPrompt(PostureMetricView view) {
        PostureMetric metric = view.metric();
        String measured = view.measured() == null ? "" : view.measured().trim();
        return switch (metric) {
            case TRUNK_LEAN -> trunkLeanTopic(measured);
            case INITIAL_KNEE_FLEXION -> kneeFlexionTopic(measured);
            case FOOT_STRIKE_PATTERN -> footStrikeTopic(view.status());
        };
    }

    private String trunkLeanTopic(String measured) {
        StringBuilder sb = new StringBuilder(
                "Topic: Excessive forward trunk lean during sustained distance running");
        if (!measured.isEmpty()) {
            sb.append(" (observed sagittal trunk inclination ").append(measured).append(" degrees)");
        }
        sb.append(". Discuss biomechanical effects on hip extensor demand, vertical center-of-mass")
          .append(" displacement, running economy and overuse injury risk; describe sagittal-plane")
          .append(" mechanisms and evidence-based corrective interventions for distance runners.");
        return sb.toString();
    }

    private String kneeFlexionTopic(String measured) {
        StringBuilder sb = new StringBuilder(
                "Topic: Knee flexion angle at initial foot contact (touchdown) during distance running");
        if (!measured.isEmpty()) {
            sb.append(" (observed flexion ").append(measured).append(" degrees at initial contact)");
        }
        sb.append(". Discuss relationship between landing knee flexion and vertical impact loading")
          .append(" rate, eccentric quadriceps work, patellofemoral joint loading, and overuse injury")
          .append(" risk in endurance runners; describe corrective approaches to increase landing")
          .append(" knee flexion.");
        return sb.toString();
    }

    private String footStrikeTopic(String pattern) {
        String descriptor = footStrikeDescriptor(pattern);
        return "Topic: " + descriptor + " during sustained distance running."
                + " Discuss biomechanical implications for vertical impact peak, instantaneous"
                + " loading rate, ankle and knee joint loading, running economy, and overuse"
                + " injury risk; describe evidence on transitioning foot strike pattern in"
                + " distance runners.";
    }

    private String footStrikeDescriptor(String pattern) {
        if (pattern == null) return "Foot strike pattern";
        return switch (pattern.trim().toUpperCase()) {
            case "RFS" -> "Rearfoot strike (heel strike) pattern";
            case "MFS" -> "Midfoot strike pattern";
            case "FFS" -> "Forefoot strike pattern";
            default -> pattern + " foot strike pattern";
        };
    }
}
