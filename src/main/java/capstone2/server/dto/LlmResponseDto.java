package capstone2.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmResponseDto(
        List<PerMetric> perMetric,
        String totalFeedback
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerMetric(String type, String summary, String improved, String problem) {
    }

    public Optional<PerMetric> findByType(String type) {
        if (perMetric == null) return Optional.empty();
        return perMetric.stream()
                .filter(p -> p != null && type.equals(p.type()))
                .findFirst();
    }
}
