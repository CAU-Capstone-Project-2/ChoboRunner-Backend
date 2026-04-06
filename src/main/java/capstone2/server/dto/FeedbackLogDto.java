package capstone2.server.dto;

import lombok.*;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class FeedbackLogDto {
    private Long id;
    private Long runId;
    private LocalTime timestamp;
    private String issueType;
    private String message;
    private String severity;
    private Boolean isImproved;
}
