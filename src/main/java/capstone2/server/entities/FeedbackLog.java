// File: `src/main/java/capstone2/server/entities/FeedbackLog.java`
package capstone2.server.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "FEEDBACK_LOG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class FeedbackLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(hidden = true)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", referencedColumnName = "id", nullable = false)
    private RunSession runSession;

    @Column(columnDefinition = "TIME", nullable = false)
    @Schema(description = "Timestamp of the feedback log in HH:mm:ss format", example = "00:02:15")
    private LocalTime timestamp;

    @Column(name = "issue_type", nullable = false)
    private String issueType;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private String severity;

    @Column(name = "is_improved")
    private Boolean isImproved;
}
