
package capstone2.server.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class DetailedReportDto {
    private Long id;
    private Long reportId;
    private String type;
    private String status;
    private String measured;
    private String stdVal;
    private Integer refMin;
    private Integer refMax;
    private Double sensitivity;
    private String problem;
    private String improved;
    private String summary;
    private Integer score;
}