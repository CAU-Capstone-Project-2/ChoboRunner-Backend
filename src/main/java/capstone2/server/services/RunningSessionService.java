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
                .videoS3Key(runSessionDto.getVideoS3Key())
                .build();
        return toDto(repo.save(s));
    }
    public Optional<RunSessionDto> findDtoById(Long id){ return repo.findById(id).map(this::toDto); }
    public List<RunSessionDto> findAllDto(){ return repo.findAll().stream().map(this::toDto).toList(); }
    public List<RunSessionDto> findByUserIdDto(Long userId){ return repo.findByUserId(userId).stream().map(this::toDto).toList(); }
    public RunSessionDto update(Long id, RunSessionDto dto){
        RunSession s = repo.findById(id).orElseThrow();
        // 부분 업데이트: null로 들어온 필드는 기존 값을 보존한다.
        // (요청에 일부 필드만 담겨 와도 저장된 데이터가 null로 덮어써지지 않도록)
        if (dto.getUserId() != null) {
            s.setUser(userRepo.findById(dto.getUserId()).orElseThrow());
        }
        if (dto.getCreatedDate() != null) s.setCreatedDate(dto.getCreatedDate());
        if (dto.getMode() != null) s.setMode(dto.getMode());
        if (dto.getStatus() != null) s.setStatus(dto.getStatus());
        // videoS3Key는 오버레이 플로우(updateVideoS3Key)가 전담한다.
        if (dto.getVideoS3Key() != null) s.setVideoS3Key(dto.getVideoS3Key());
        if (dto.getDuration() != null) s.setDuration(dto.getDuration());
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
                .videoS3Key(s.getVideoS3Key())
                .duration(s.getDuration())
                .build();
    }

    public Optional<RunSession> findById(Long runId) {
        return repo.findById(runId);}

    /**
     * 오버레이 완료 시 영상 S3 key만 갱신한다 (덮어쓰기).
     * 처리 도중 run 이 삭제됐으면 empty 를 반환한다(500 대신 호출부에서 명확히 처리).
     */
    public Optional<RunSessionDto> updateVideoS3Key(Long id, String videoS3Key) {
        int updated = repo.updateVideoS3Key(id, videoS3Key);
        if (updated == 0) return Optional.empty();
        return repo.findById(id).map(this::toDto);
    }
}
