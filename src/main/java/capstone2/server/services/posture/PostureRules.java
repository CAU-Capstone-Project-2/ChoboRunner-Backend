package capstone2.server.services.posture;

import capstone2.server.config.PostureProperties;
import capstone2.server.domain.MetricEvaluation;
import capstone2.server.domain.Verdict;
import capstone2.server.dto.PoseAnalysisInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PostureRules {

    private final PostureProperties props;

    public List<MetricEvaluation> evaluateAll(List<PoseAnalysisInput.Metric> metrics) {
        List<MetricEvaluation> result = new ArrayList<>();
        if (metrics == null) return result;

        for (PoseAnalysisInput.Metric m : metrics) {
            if (m == null || m.metricName() == null) continue;
            switch (m.metricName()) {
                case "trunk_lean" -> result.add(evalTrunk(m));
                case "initial_knee_flexion" -> result.add(evalKnee(m));
                case "foot_strike_pattern" -> {
                    MetricEvaluation foot = evalFoot(m);
                    if (!foot.skipped()) result.add(foot);
                }
                default -> { /* ignore unknown metric names */ }
            }
        }
        return result;
    }

    private MetricEvaluation evalTrunk(PoseAnalysisInput.Metric m) {
        PostureProperties.TrunkLean t = props.getTrunkLean();
        Verdict v = trunkVerdict(m.value(), t);
        return MetricEvaluation.builder()
                .metricName("trunk_lean")
                .rawValue(m.value())
                .status(v.name())
                .verdict(v)
                .problem(ProblemLabels.of("trunk_lean", v))
                .refMin(t.getRefMin())
                .refMax(t.getRefMax())
                .stdVal(t.getStdVal())
                .sensitivity(t.getSensitivity())
                .skipped(false)
                .build();
    }

    private Verdict trunkVerdict(Double value, PostureProperties.TrunkLean t) {
        if (value == null) return Verdict.UNRELIABLE;
        if (value < t.getUnreliableMin() || value > t.getUnreliableMax()) return Verdict.UNRELIABLE;
        if (value < t.getOptimalMin())   return Verdict.CAUTION;     // -15 ~ 5 (수직/약한 lean)
        if (value < t.getOptimalMax())   return Verdict.OPTIMAL;     // 5 ~ 10
        return Verdict.SUBOPTIMAL;                                   // >= 10
    }

    private MetricEvaluation evalKnee(PoseAnalysisInput.Metric m) {
        PostureProperties.KneeFlexion k = props.getKneeFlexion();
        Verdict v = kneeVerdict(m.value(), k);
        return MetricEvaluation.builder()
                .metricName("initial_knee_flexion")
                .rawValue(m.value())
                .status(v.name())
                .verdict(v)
                .problem(ProblemLabels.of("initial_knee_flexion", v))
                .refMin(k.getRefMin())
                .refMax(k.getRefMax())
                .stdVal(k.getStdVal())
                .sensitivity(k.getSensitivity())
                .skipped(false)
                .build();
    }

    private Verdict kneeVerdict(Double value, PostureProperties.KneeFlexion k) {
        if (value == null) return Verdict.UNRELIABLE;
        if (value >= k.getUnreliableMin())   return Verdict.UNRELIABLE; // >= 50
        if (value < k.getHighRiskMax())      return Verdict.HIGH_RISK;  // 0 ~ 10
        if (value < k.getCautionLowMax())    return Verdict.CAUTION;    // 10 ~ 15
        if (value < k.getOptimalMax())       return Verdict.OPTIMAL;    // 15 ~ 25
        if (value < k.getCautionHighMax())   return Verdict.CAUTION;    // 25 ~ 30
        return Verdict.SUBOPTIMAL;                                      // 30 ~ 50
    }

    private MetricEvaluation evalFoot(PoseAnalysisInput.Metric m) {
        String pattern = m.patternTendency();
        if (pattern == null || pattern.isBlank()) {
            return MetricEvaluation.builder().metricName("foot_strike_pattern").skipped(true).build();
        }
        String upper = pattern.trim().toUpperCase();
        String status;
        Verdict verdict;
        switch (upper) {
            case "RFS", "MFS", "FFS" -> {
                status = upper;
                verdict = null;
            }
            case "UNCLEAR" -> {
                status = Verdict.UNRELIABLE.name();
                verdict = Verdict.UNRELIABLE;
            }
            default -> {
                return MetricEvaluation.builder().metricName("foot_strike_pattern").skipped(true).build();
            }
        }
        return MetricEvaluation.builder()
                .metricName("foot_strike_pattern")
                .rawValue(m.value())
                .status(status)
                .verdict(verdict)
                .problem(null)
                .skipped(false)
                .build();
    }
}
