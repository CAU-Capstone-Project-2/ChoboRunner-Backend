// File: `src/main/java/capstone2/server/repositories/RunSessionRepository.java`
package capstone2.server.repositories;

import capstone2.server.entities.RunSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RunSessionRepository extends JpaRepository<RunSession, Long> {
    List<RunSession> findByUserId(Long userId);

    /**
     * videoS3Key 만 DB 에 직접 갱신한다(영속성 컨텍스트 우회).
     * @return 영향받은 행 수 — 0 이면 해당 run 이 존재하지 않음(삭제됨).
     */
    @Modifying
    @Query("update RunSession r set r.videoS3Key = :videoS3Key where r.id = :id")
    int updateVideoS3Key(@Param("id") Long id, @Param("videoS3Key") String videoS3Key);
}
