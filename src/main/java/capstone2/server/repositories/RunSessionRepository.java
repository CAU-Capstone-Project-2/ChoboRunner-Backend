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
     * <p>OSIV(open-in-view=true 기본값)로 요청 내내 1차 캐시가 살아있어,
     * 앞서 findById 로 적재된 엔티티가 이 벌크 UPDATE 를 못 보고 stale(videoS3Key=null) 로 남는다.
     * clearAutomatically=true 로 갱신 직후 영속성 컨텍스트를 비워, 뒤따르는 findById 가
     * DB 에서 갱신된 값을 다시 읽도록 한다(응답 DTO 가 null 로 나가는 문제 방지).
     * @return 영향받은 행 수 — 0 이면 해당 run 이 존재하지 않음(삭제됨).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RunSession r set r.videoS3Key = :videoS3Key where r.id = :id")
    int updateVideoS3Key(@Param("id") Long id, @Param("videoS3Key") String videoS3Key);
}
