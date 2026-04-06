// File: `src/main/java/capstone2/server/repositories/FeedbackLogRepository.java`
package capstone2.server.repositories;

import capstone2.server.entities.FeedbackLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeedbackLogRepository extends JpaRepository<FeedbackLog, Long> {
    List<FeedbackLog> findByRunSessionId(Long runId);
}
