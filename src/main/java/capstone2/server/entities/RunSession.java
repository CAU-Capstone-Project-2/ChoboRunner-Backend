// File: `src/main/java/capstone2/server/entities/RunSession.java`
package capstone2.server.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import java.time.LocalDateTime;

@Entity
@Table(name = "RUN_SESSION")
// 변경된 컬럼만 UPDATE 한다. PUT(전체 수정)과 videoS3Key 부분 갱신이 동시에 일어날 때
// 손대지 않은 videoS3Key가 stale 값으로 덮어써지는 lost update를 막는다.
@DynamicUpdate
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class RunSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(hidden = true)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Column(name = "created_date", columnDefinition = "DATETIME", nullable = false)
    private LocalDateTime createdDate;

    @Column(nullable = false)
    private String mode;

    @Column(nullable = false)
    private String status;

    // RunSession 생성 시점에는 영상이 없다.
    // 러닝 종료 후 오버레이 합성이 끝나면 최종 영상의 S3 key가 채워진다.
    @Column(name = "video_s3_key")
    private String videoS3Key;

    private Integer duration; // sec
}
