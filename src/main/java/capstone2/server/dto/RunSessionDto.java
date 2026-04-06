package capstone2.server.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class RunSessionDto {
    private Long id;
    private Long userId;
    private LocalDateTime createdDate;
    private String mode;
    private String status;
    private String videoS3Url;
    private Integer duration;
}
