// File: `src/main/java/capstone2/server/services/HighlightService.java`
package capstone2.server.services;

import capstone2.server.dto.HighlightDto;
import capstone2.server.entities.Highlight;
import capstone2.server.repositories.HighlightRepository;
import capstone2.server.repositories.RunSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class HighlightService {
    private final HighlightRepository repo;
    private final RunSessionRepository runRepo;

    public HighlightDto create(HighlightDto dto){
        Highlight h = Highlight.builder()
                .runSession(runRepo.findById(dto.getRunId()).orElseThrow())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .problem(dto.getProblem())
                .improved(dto.getImproved())
                .issueType(dto.getIssueType())
                .build();
        return toDto(repo.save(h));
    }
    public Optional<HighlightDto> findDtoById(Long id){ return repo.findById(id).map(this::toDto); }
    public List<HighlightDto> findAllDto(){ return repo.findAll().stream().map(this::toDto).toList(); }
    public List<HighlightDto> findByRunIdDto(Long runId){ return repo.findByRunSessionId(runId).stream().map(this::toDto).toList(); }
    public HighlightDto update(Long id, HighlightDto dto){
        Highlight existing = repo.findById(id).orElseThrow();
        existing.setRunSession(runRepo.findById(dto.getRunId()).orElseThrow());
        existing.setStartTime(dto.getStartTime());
        existing.setEndTime(dto.getEndTime());
        existing.setIssueType(dto.getIssueType());
        existing.setProblem(dto.getProblem());
        existing.setImproved(dto.getImproved());
        return toDto(repo.save(existing));
    }
    public void delete(Long id){ repo.deleteById(id); }

    private HighlightDto toDto(Highlight h) {
        return HighlightDto.builder()
                .id(h.getId())
                .runId(h.getRunSession().getId())
                .startTime(h.getStartTime())
                .endTime(h.getEndTime())
                .issueType(h.getIssueType())
                .problem(h.getProblem())
                .improved(h.getImproved())
                .build();
    }
}
