package capstone2.server.services;

import capstone2.server.dto.ReportDto;
import capstone2.server.entities.Report;
import capstone2.server.entities.RunSession;
import capstone2.server.repositories.ReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {
    private final ReportRepository repo;
    private final RunningSessionService runningSessionService;

    public ReportDto create(ReportDto dto) {
        Report r = toEntity(dto);
        return toDto(repo.save(r));
    }

    public Optional<ReportDto> findDtoById(Long id) { return repo.findById(id).map(this::toDto); }
    public List<ReportDto> findAllDto() { return repo.findAll().stream().map(this::toDto).toList(); }
    public ReportDto update(Long id, ReportDto dto) {
        Report existing = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Report not found: " + id));
        applyDtoToEntity(dto, existing);
        return toDto(repo.save(existing));
    }
    public void delete(Long id) { repo.deleteById(id); }

    private Report toEntity(ReportDto dto){
        Report r = new Report();
//        if (dto.getId() != null) r.setId(dto.getId());
        applyDtoToEntity(dto, r);
        return r;
    }

    private ReportDto toDto(Report report) {
        return ReportDto.builder()
                .id(report.getId())
                .runId(report.getRunSession().getId())
                .totalFeedback(report.getTotalFeedback())
                .build();
    }

    private void applyDtoToEntity(ReportDto dto, Report r){
        RunSession runSession = runningSessionService.findById(dto.getRunId())
                .orElseThrow(() -> new EntityNotFoundException("RunSession not found: " + dto.getRunId()));;
        r.setRunSession(runSession);
        r.setTotalFeedback(dto.getTotalFeedback());
    }
}

