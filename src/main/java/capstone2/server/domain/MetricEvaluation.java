package capstone2.server.domain;

import lombok.Builder;

@Builder
public record MetricEvaluation(
        String metricName,
        Double rawValue,
        String status,
        Verdict verdict,
        String problem,
        Integer refMin,
        Integer refMax,
        Double stdVal,
        Double sensitivity,
        boolean skipped
) {
}
