package capstone2.server.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class UserDto {
    private Long id;
    private String username;
    private String password;
    private String runningLevel;
    private String description;
    private String goal;
    private Integer age;
    private Integer height;
}
