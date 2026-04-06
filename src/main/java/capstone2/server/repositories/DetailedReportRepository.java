// File: `src/main/java/capstone2/server/repositories/DetailedReportRepository.java`
package capstone2.server.repositories;

import capstone2.server.entities.DetailedReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DetailedReportRepository extends JpaRepository<DetailedReport, Long> {
    List<DetailedReport> findByReportId(Long reportId);
}
