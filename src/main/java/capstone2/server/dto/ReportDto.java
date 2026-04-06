package capstone2.server.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class ReportDto {
    private Long id;
    private Long runId;
    private String totalFeedback;
}
