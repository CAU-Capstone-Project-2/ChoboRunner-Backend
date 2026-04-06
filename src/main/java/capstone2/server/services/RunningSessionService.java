// File: `src/main/java/capstone2/server/services/RunningSessionService.java`
package capstone2.server.services;

import capstone2.server.dto.RunSessionDto;
import capstone2.server.entities.RunSession;
import capstone2.server.entities.User;
import capstone2.server.repositories.RunSessionRepository;
import capstone2.server.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class RunningSessionService {
    private final RunSessionRepository repo;
    private final UserRepository userRepo;

    public RunSessionDto create(RunSessionDto runSessionDto){
        User user = userRepo.findById(runSessionDto.getUserId()).orElseThrow();
        RunSession s = RunSession.builder().user(user)
                .createdDate(runSessionDto.getCreatedDate())
                .mode(runSessionDto.getMode())
                .duration(runSessionDto.getDuration())
                .status(runSessionDto.getStatus())
                .videoS3Url(runSessionDto.getVideoS3Url())
                .build();
        return toDto(repo.save(s));
    }
    public Optional<RunSessionDto> findDtoById(Long id){ return repo.findById(id).map(this::toDto); }
    public List<RunSessionDto> findAllDto(){ return repo.findAll().stream().map(this::toDto).toList(); }
    public List<RunSessionDto> findByUserIdDto(Long userId){ return repo.findByUserId(userId).stream().map(this::toDto).toList(); }
    public RunSessionDto update(Long id, RunSessionDto dto){
        RunSession s = repo.findById(id).orElseThrow();
        s.setUser(userRepo.findById(dto.getUserId()).orElseThrow());
        s.setCreatedDate(dto.getCreatedDate());
        s.setMode(dto.getMode());
        s.setStatus(dto.getStatus());
        s.setVideoS3Url(dto.getVideoS3Url());
        s.setDuration(dto.getDuration());
        return toDto(repo.save(s));
    }
    public void delete(Long id){ repo.deleteById(id); }

    private RunSessionDto toDto(RunSession s) {
        return RunSessionDto.builder()
                .id(s.getId())
                .userId(s.getUser().getId())
                .createdDate(s.getCreatedDate())
                .mode(s.getMode())
                .status(s.getStatus())
                .videoS3Url(s.getVideoS3Url())
                .duration(s.getDuration())
                .build();
    }

    public Optional<RunSession> findById(Long runId) {
        return repo.findById(runId);}
}
