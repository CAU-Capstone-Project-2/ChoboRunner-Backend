// File: `src/main/java/capstone2/server/repositories/HighlightRepository.java`
package capstone2.server.repositories;

import capstone2.server.entities.Highlight;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HighlightRepository extends JpaRepository<Highlight, Long> {
    List<Highlight> findByRunSessionId(Long runId);
}
