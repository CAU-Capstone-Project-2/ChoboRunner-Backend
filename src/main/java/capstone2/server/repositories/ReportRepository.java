// File: `src/main/java/capstone2/server/repositories/ReportRepository.java`
package capstone2.server.repositories;

import capstone2.server.entities.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByRunSessionId(Long runId);
}
