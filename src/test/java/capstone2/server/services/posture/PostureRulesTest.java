package capstone2.server.services.posture;

import capstone2.server.config.PostureProperties;
import capstone2.server.domain.MetricEvaluation;
import capstone2.server.domain.Verdict;
import capstone2.server.dto.PoseAnalysisInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostureRulesTest {

    private PostureRules rules;

    @BeforeEach
    void setUp() {
        rules = new PostureRules(propsFixture());
    }

    @ParameterizedTest
    @CsvSource({
            "-20.0,UNRELIABLE",
            "-15.001,UNRELIABLE",
            "-15.0,CAUTION",
            "-1.0,CAUTION",
            "0.0,CAUTION",
            "4.99,CAUTION",
            "5.0,OPTIMAL",
            "9.99,OPTIMAL",
            "10.0,SUBOPTIMAL",
            "20.0,SUBOPTIMAL",
            "45.0,SUBOPTIMAL",
            "45.001,UNRELIABLE",
            "100.0,UNRELIABLE"
    })
    void trunkLeanVerdictBoundaries(double value, String expected) {
        var metric = trunkMetric(value);
        var eval = rules.evaluateAll(List.of(metric)).get(0);

        assertThat(eval.metricName()).isEqualTo("trunk_lean");
        assertThat(eval.verdict()).isEqualTo(Verdict.valueOf(expected));
        assertThat(eval.status()).isEqualTo(expected);
        assertThat(eval.refMin()).isEqualTo(5);
        assertThat(eval.refMax()).isEqualTo(10);
        assertThat(eval.stdVal()).isEqualTo(7.5);
        assertThat(eval.skipped()).isFalse();
    }

    @Test
    void trunkLeanWithNullValueIsUnreliable() {
        var metric = new PoseAnalysisInput.Metric(
                "trunk_lean", "primary", false, null, null, null, null);
        var eval = rules.evaluateAll(List.of(metric)).get(0);

        assertThat(eval.verdict()).isEqualTo(Verdict.UNRELIABLE);
        assertThat(eval.problem()).isEqualTo("측정 신뢰도 낮음");
    }

    @ParameterizedTest
    @CsvSource({
            "0.0,HIGH_RISK",
            "9.99,HIGH_RISK",
            "10.0,CAUTION",
            "14.99,CAUTION",
            "15.0,OPTIMAL",
            "20.0,OPTIMAL",
            "24.99,OPTIMAL",
            "25.0,CAUTION",
            "29.99,CAUTION",
            "30.0,SUBOPTIMAL",
            "49.99,SUBOPTIMAL",
            "50.0,UNRELIABLE",
            "80.78,UNRELIABLE"
    })
    void kneeFlexionVerdictBoundaries(double value, String expected) {
        var metric = kneeMetric(value);
        var eval = rules.evaluateAll(List.of(metric)).get(0);

        assertThat(eval.metricName()).isEqualTo("initial_knee_flexion");
        assertThat(eval.verdict()).isEqualTo(Verdict.valueOf(expected));
        assertThat(eval.refMin()).isEqualTo(15);
        assertThat(eval.refMax()).isEqualTo(25);
    }

    @ParameterizedTest
    @CsvSource({
            "RFS,RFS",
            "MFS,MFS",
            "FFS,FFS",
            "rfs,RFS",
            " mfs ,MFS"
    })
    void footStrikePatternStoresPatternName(String input, String expectedStatus) {
        var metric = footMetric(input);
        var eval = rules.evaluateAll(List.of(metric)).get(0);

        assertThat(eval.metricName()).isEqualTo("foot_strike_pattern");
        assertThat(eval.status()).isEqualTo(expectedStatus);
        assertThat(eval.verdict()).isNull();
        assertThat(eval.problem()).isNull();
        assertThat(eval.skipped()).isFalse();
    }

    @Test
    void footStrikeUnclearMapsToUnreliable() {
        var metric = footMetric("unclear");
        var eval = rules.evaluateAll(List.of(metric)).get(0);

        assertThat(eval.status()).isEqualTo("UNRELIABLE");
        assertThat(eval.verdict()).isEqualTo(Verdict.UNRELIABLE);
    }

    @Test
    void footStrikeNullPatternIsSkipped() {
        var metric = footMetric(null);
        List<MetricEvaluation> evals = rules.evaluateAll(List.of(metric));
        assertThat(evals).isEmpty();
    }

    @Test
    void footStrikeUnknownPatternIsSkipped() {
        var metric = footMetric("garbage");
        List<MetricEvaluation> evals = rules.evaluateAll(List.of(metric));
        assertThat(evals).isEmpty();
    }

    @Test
    void evaluatesAllThreeMetricsAndPreservesOrder() {
        var input = List.of(
                trunkMetric(7.0),
                kneeMetric(20.0),
                footMetric("RFS")
        );
        List<MetricEvaluation> evals = rules.evaluateAll(input);

        assertThat(evals).extracting(MetricEvaluation::metricName)
                .containsExactly("trunk_lean", "initial_knee_flexion", "foot_strike_pattern");
        assertThat(evals.get(0).verdict()).isEqualTo(Verdict.OPTIMAL);
        assertThat(evals.get(1).verdict()).isEqualTo(Verdict.OPTIMAL);
        assertThat(evals.get(2).status()).isEqualTo("RFS");
    }

    @Test
    void unknownMetricNamesAreIgnored() {
        var unknown = new PoseAnalysisInput.Metric(
                "stride_length", "primary", false, 1.5, null, null, null);
        assertThat(rules.evaluateAll(List.of(unknown))).isEmpty();
    }

    @Test
    void nullMetricsListReturnsEmpty() {
        assertThat(rules.evaluateAll(null)).isEmpty();
    }

    // --- helpers ---

    private PoseAnalysisInput.Metric trunkMetric(Double value) {
        return new PoseAnalysisInput.Metric(
                "trunk_lean", "primary", false, value, null, null, null);
    }

    private PoseAnalysisInput.Metric kneeMetric(Double value) {
        return new PoseAnalysisInput.Metric(
                "initial_knee_flexion", "primary", false, value, null, null, null);
    }

    private PoseAnalysisInput.Metric footMetric(String pattern) {
        return new PoseAnalysisInput.Metric(
                "foot_strike_pattern", "secondary", true, 130.0, null, null, pattern);
    }

    private static PostureProperties propsFixture() {
        var p = new PostureProperties();
        var t = p.getTrunkLean();
        t.setOptimalMin(5.0);
        t.setOptimalMax(10.0);
        t.setSuboptimalMin(10.0);
        t.setUnreliableMin(-15.0);
        t.setUnreliableMax(45.0);
        t.setStdVal(7.5);
        t.setRefMin(5);
        t.setRefMax(10);
        t.setSensitivity(2.0);

        var k = p.getKneeFlexion();
        k.setHighRiskMax(10.0);
        k.setCautionLowMax(15.0);
        k.setOptimalMin(15.0);
        k.setOptimalMax(25.0);
        k.setCautionHighMax(30.0);
        k.setSuboptimalMin(30.0);
        k.setUnreliableMin(50.0);
        k.setStdVal(20.0);
        k.setRefMin(15);
        k.setRefMax(25);
        k.setSensitivity(2.0);
        return p;
    }
}
