// File: `src/main/java/capstone2/server/entities/DetailedReport.java`
package capstone2.server.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "DETAILED_REPORT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class DetailedReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(hidden = true)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", referencedColumnName = "id", nullable = false)
    private Report report;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String measured;

    @Column(name = "std_val", nullable = false)
    private String stdVal;

    @Column(name = "ref_min")
    private Integer refMin;

    @Column(name= "ref_max")
    private Integer refMax;

    private Double sensitivity;

    private Integer score;

    private String problem;

    private String improved;

    private String summary;
}
