// File: `src/main/java/capstone2/server/entities/Highlight.java`
package capstone2.server.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "HIGHLIGHT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class Highlight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(hidden = true)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", referencedColumnName = "id", nullable = false)
    private RunSession runSession;

    @Column(name = "start_time", columnDefinition = "TIME", nullable = false)
    @Schema(description = "Highlight start time in HH:mm:ss format", example = "00:00:45")
    private LocalTime startTime;

    @Column(name = "end_time", columnDefinition = "TIME", nullable = false)
    @Schema(description = "Highlight end time in HH:mm:ss format", example = "00:01:30")
    private LocalTime endTime;

    @Column(name = "issue_type", nullable = false)
    private String issueType;

    @Column(nullable = false)
    private String problem;

    @Column(nullable = false)
    private String improved;
}
