package capstone2.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PoseAnalysisInput(
        String runId,
        String overlayS3Url,
        List<Metric> metrics
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Metric(
            String metricName,
            String tier,
            Boolean experimental,
            Double value,
            String category,
            String interpretation,
            String patternTendency
    ) {
    }
}
