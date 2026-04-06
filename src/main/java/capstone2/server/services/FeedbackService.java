// File: `src/main/java/capstone2/server/services/FeedbackService.java`
package capstone2.server.services;

import capstone2.server.dto.FeedbackLogDto;
import capstone2.server.entities.FeedbackLog;
import capstone2.server.repositories.FeedbackLogRepository;
import capstone2.server.repositories.RunSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class FeedbackService {
    private final FeedbackLogRepository repo;
    private final RunSessionRepository runRepo;

    public FeedbackLogDto create(FeedbackLogDto dto){
        FeedbackLog f = FeedbackLog.builder()
            .runSession(runRepo.getReferenceById(dto.getRunId()))
            .message(dto.getMessage())
            .isImproved(dto.getIsImproved())
            .timestamp(dto.getTimestamp())
            .issueType(dto.getIssueType())
            .severity(dto.getSeverity())
            .build();
        return toDto(repo.save(f));
    }
    public Optional<FeedbackLogDto> findDtoById(Long id){ return repo.findById(id).map(this::toDto); }
    public List<FeedbackLogDto> findAllDto(){ return repo.findAll().stream().map(this::toDto).toList(); }
    public List<FeedbackLogDto> findByRunIdDto(Long runId){ return repo.findByRunSessionId(runId).stream().map(this::toDto).toList(); }
    public FeedbackLogDto update(Long id, FeedbackLogDto dto){
        FeedbackLog f = repo.findById(id).orElseThrow();
        f.setRunSession(runRepo.getReferenceById(dto.getRunId()));
        f.setTimestamp(dto.getTimestamp());
        f.setIssueType(dto.getIssueType());
        f.setMessage(dto.getMessage());
        f.setSeverity(dto.getSeverity());
        f.setIsImproved(dto.getIsImproved());
        return toDto(repo.save(f));
    }
    public void delete(Long id){ repo.deleteById(id); }

    private FeedbackLogDto toDto(FeedbackLog f) {
        return FeedbackLogDto.builder()
                .id(f.getId())
                .runId(f.getRunSession().getId())
                .timestamp(f.getTimestamp())
                .issueType(f.getIssueType())
                .message(f.getMessage())
                .severity(f.getSeverity())
                .isImproved(f.getIsImproved())
                .build();
    }
}
