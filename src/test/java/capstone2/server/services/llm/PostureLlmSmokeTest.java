package capstone2.server.services.llm;

import capstone2.server.dto.LlmResponseDto;
import capstone2.server.dto.PoseAnalysisInput;
import capstone2.server.services.posture.PostureMetric;
import capstone2.server.services.posture.PostureMetricView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring 측 LLM 종단 스모크 테스트 — PostureLlmClient 가 실제 OpenAI 모델
 * (gpt-5.x 계열 등) 로 호출 성공하고 JSON 응답을 파싱하는지 확인.
 *
 * <p>실행 조건:
 * <ul>
 *   <li>{@code POSTURE_RAG_IT=true} — 가드 (RAG IT 와 동일 가드 재사용)</li>
 *   <li>{@code OPENAI_API_KEY}</li>
 *   <li>{@code OPENAI_CHAT_MODEL} (선택, 기본 gpt-5.5)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "rag-it"})
@EnabledIfEnvironmentVariable(named = "POSTURE_RAG_IT", matches = "true")
class PostureLlmSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(PostureLlmSmokeTest.class);

    @Autowired
    private PostureLlmClient llmClient;

    @Test
    void generatesValidJsonResponse() {
        assertThat(llmClient.isEnabled()).as("LLM 클라이언트 활성화").isTrue();

        List<PostureMetricView> views = List.of(
                new PostureMetricView(PostureMetric.TRUNK_LEAN, "9.5", "주의",
                        List.of("상체가 앞으로 너무 기울어졌어요")),
                new PostureMetricView(PostureMetric.INITIAL_KNEE_FLEXION, "18.0", "정상", List.of()),
                new PostureMetricView(PostureMetric.FOOT_STRIKE_PATTERN, "6.0", "RFS",
                        List.of("발 뒷꿈치 착지 패턴이 관찰됩니다"))
        );
        PoseAnalysisInput input = new PoseAnalysisInput(
                "analysis_result", "success", null, "left",
                null, null, null,
                "primary_reason", List.of("rc_trunk"), null, List.of()
        );

        LlmResponseDto resp = llmClient.generate(views, input);
        assertThat(resp).as("LLM 응답이 null 이 아니어야 함 (API 호출 + JSON 파싱 성공)").isNotNull();
        assertThat(resp.perMetric()).as("perMetric 리스트가 비어있지 않음").isNotEmpty();
        assertThat(resp.totalFeedback()).as("totalFeedback 텍스트 존재").isNotBlank();

        log.info("=== LLM smoke 응답 ===");
        log.info("totalFeedback: {}", resp.totalFeedback());
        if (resp.perMetric() != null) {
            for (LlmResponseDto.PerMetric pm : resp.perMetric()) {
                if (pm != null) {
                    log.info("  [{}] summary={}, improved={}, problem={}",
                            pm.type(), pm.summary(), pm.improved(), pm.problem());
                }
            }
        }
    }
}
