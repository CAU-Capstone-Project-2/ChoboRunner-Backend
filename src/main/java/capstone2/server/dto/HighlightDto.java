package capstone2.server.dto;

import lombok.*;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class HighlightDto {
    private Long id;
    private Long runId;
    private LocalTime startTime;
    private LocalTime endTime;
    private String issueType;
    private String problem;
    private String improved;
}
