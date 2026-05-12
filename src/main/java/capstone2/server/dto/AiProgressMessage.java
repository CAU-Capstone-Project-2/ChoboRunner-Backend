package capstone2.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiProgressMessage {

    private String type;

    @JsonProperty("elapsed_sec")
    private Double elapsedSec;

    @JsonProperty("feedback_messages")
    private List<FeedbackMessage> feedbackMessages;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeedbackMessage {
        private String category;
        private String metric;

        @JsonProperty("display_text")
        private String displayText;
    }
}