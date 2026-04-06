// File: `src/main/java/capstone2/server/repositories/RunSessionRepository.java`
package capstone2.server.repositories;

import capstone2.server.entities.RunSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RunSessionRepository extends JpaRepository<RunSession, Long> {
    List<RunSession> findByUserId(Long userId);
}
