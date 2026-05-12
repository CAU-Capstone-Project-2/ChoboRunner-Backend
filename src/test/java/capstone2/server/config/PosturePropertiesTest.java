package capstone2.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PosturePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsThresholdValuesFromProperties() {
        runner.withPropertyValues(
                        "posture.trunk-lean.optimal-min=5.0",
                        "posture.trunk-lean.optimal-max=10.0",
                        "posture.trunk-lean.suboptimal-min=10.0",
                        "posture.trunk-lean.unreliable-min=-15.0",
                        "posture.trunk-lean.unreliable-max=45.0",
                        "posture.trunk-lean.std-val=7.5",
                        "posture.trunk-lean.ref-min=5",
                        "posture.trunk-lean.ref-max=10",
                        "posture.trunk-lean.sensitivity=2.0",
                        "posture.knee-flexion.high-risk-max=10.0",
                        "posture.knee-flexion.caution-low-max=15.0",
                        "posture.knee-flexion.optimal-min=15.0",
                        "posture.knee-flexion.optimal-max=25.0",
                        "posture.knee-flexion.caution-high-max=30.0",
                        "posture.knee-flexion.suboptimal-min=30.0",
                        "posture.knee-flexion.unreliable-min=50.0",
                        "posture.knee-flexion.std-val=20.0",
                        "posture.knee-flexion.ref-min=15",
                        "posture.knee-flexion.ref-max=25",
                        "posture.knee-flexion.sensitivity=2.0",
                        "posture.llm.timeout-seconds=42")
                .run(context -> {
                    PostureProperties props = context.getBean(PostureProperties.class);

                    assertThat(props.getTrunkLean().getOptimalMin()).isEqualTo(5.0);
                    assertThat(props.getTrunkLean().getOptimalMax()).isEqualTo(10.0);
                    assertThat(props.getTrunkLean().getUnreliableMin()).isEqualTo(-15.0);
                    assertThat(props.getTrunkLean().getUnreliableMax()).isEqualTo(45.0);
                    assertThat(props.getTrunkLean().getStdVal()).isEqualTo(7.5);
                    assertThat(props.getTrunkLean().getRefMin()).isEqualTo(5);
                    assertThat(props.getTrunkLean().getRefMax()).isEqualTo(10);
                    assertThat(props.getTrunkLean().getSensitivity()).isEqualTo(2.0);

                    assertThat(props.getKneeFlexion().getHighRiskMax()).isEqualTo(10.0);
                    assertThat(props.getKneeFlexion().getOptimalMin()).isEqualTo(15.0);
                    assertThat(props.getKneeFlexion().getOptimalMax()).isEqualTo(25.0);
                    assertThat(props.getKneeFlexion().getUnreliableMin()).isEqualTo(50.0);
                    assertThat(props.getKneeFlexion().getStdVal()).isEqualTo(20.0);

                    assertThat(props.getLlm().getTimeoutSeconds()).isEqualTo(42L);
                });
    }

    @Test
    void hasDefaultLlmTimeoutWhenAbsent() {
        runner.run(context -> {
            PostureProperties props = context.getBean(PostureProperties.class);
            assertThat(props.getLlm().getTimeoutSeconds()).isEqualTo(30L);
        });
    }

    @Configuration
    @EnableConfigurationProperties(PostureProperties.class)
    static class TestConfig {
    }
}
