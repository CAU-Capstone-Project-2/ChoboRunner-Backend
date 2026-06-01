package capstone2.server.services;

import capstone2.server.dto.RunSessionDto;
import capstone2.server.entities.RunSession;
import capstone2.server.entities.User;
import capstone2.server.repositories.RunSessionRepository;
import capstone2.server.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunningSessionServiceTest {

    private RunSessionRepository repo;
    private UserRepository userRepo;
    private RunningSessionService service;

    @BeforeEach
    void setUp() {
        repo = mock(RunSessionRepository.class);
        userRepo = mock(UserRepository.class);
        service = new RunningSessionService(repo, userRepo);
    }

    private User user(Long id) {
        return User.builder().id(id).username("u").password("p").runningLevel("BEGINNER").build();
    }

    private RunSessionDto sampleDto(Long userId) {
        return RunSessionDto.builder()
                .userId(userId)
                .createdDate(LocalDateTime.of(2026, 5, 24, 10, 0))
                .mode("OUTDOOR")
                .status("DONE")
                .videoS3Key("video/k.mp4")
                .duration(1800)
                .build();
    }

    private RunSession sampleEntity(Long id, Long userId) {
        return RunSession.builder()
                .id(id)
                .user(user(userId))
                .createdDate(LocalDateTime.of(2026, 5, 24, 10, 0))
                .mode("OUTDOOR")
                .status("DONE")
                .videoS3Key("video/k.mp4")
                .duration(1800)
                .build();
    }

    @Test
    void createBindsUserAndPersists() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(repo.save(any(RunSession.class))).thenAnswer(inv -> {
            RunSession s = inv.getArgument(0);
            s.setId(100L);
            return s;
        });

        RunSessionDto result = service.create(sampleDto(1L));

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getMode()).isEqualTo("OUTDOOR");
    }

    @Test
    void createThrowsWhenUserMissing() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(sampleDto(99L)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void findDtoByIdReturnsMappedDto() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleEntity(1L, 2L)));

        Optional<RunSessionDto> result = service.findDtoById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(2L);
    }

    @Test
    void findAllDtoMapsAll() {
        when(repo.findAll()).thenReturn(List.of(sampleEntity(1L, 1L), sampleEntity(2L, 1L)));

        assertThat(service.findAllDto()).hasSize(2);
    }

    @Test
    void findByUserIdDtoDelegates() {
        when(repo.findByUserId(7L)).thenReturn(List.of(sampleEntity(1L, 7L)));

        List<RunSessionDto> result = service.findByUserIdDto(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(7L);
    }

    @Test
    void updateOverwritesFields() {
        RunSession existing = sampleEntity(5L, 1L);
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepo.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(repo.save(any(RunSession.class))).thenAnswer(inv -> inv.getArgument(0));

        RunSessionDto dto = sampleDto(2L);
        dto.setMode("TREADMILL");
        dto.setDuration(2400);

        RunSessionDto result = service.update(5L, dto);

        assertThat(result.getMode()).isEqualTo("TREADMILL");
        assertThat(result.getUserId()).isEqualTo(2L);
        assertThat(result.getDuration()).isEqualTo(2400);
    }

    @Test
    void updatePreservesVideoS3KeyWhenDtoOmitsIt() {
        // 오버레이가 저장해둔 키가, videoS3Key 없는 일반 PUT 업데이트로 null이 되면 안 됨.
        RunSession existing = sampleEntity(5L, 1L); // videoS3Key="video/k.mp4"
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepo.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(repo.save(any(RunSession.class))).thenAnswer(inv -> inv.getArgument(0));

        RunSessionDto dto = sampleDto(1L);
        dto.setVideoS3Key(null); // 클라이언트가 상태/duration만 갱신, 키 미포함
        dto.setStatus("DONE");

        RunSessionDto result = service.update(5L, dto);

        assertThat(result.getVideoS3Key()).isEqualTo("video/k.mp4");
    }

    @Test
    void updateVideoS3KeyOnlyChangesKey() {
        RunSession updated = sampleEntity(5L, 1L);
        updated.setVideoS3Key("video/new.mp4");
        when(repo.updateVideoS3Key(5L, "video/new.mp4")).thenReturn(1);
        when(repo.findById(5L)).thenReturn(Optional.of(updated));

        Optional<RunSessionDto> result = service.updateVideoS3Key(5L, "video/new.mp4");

        assertThat(result).isPresent();
        assertThat(result.get().getVideoS3Key()).isEqualTo("video/new.mp4");
        assertThat(result.get().getMode()).isEqualTo("OUTDOOR");
    }

    @Test
    void updateVideoS3KeyReturnsEmptyWhenRunDeleted() {
        when(repo.updateVideoS3Key(5L, "video/new.mp4")).thenReturn(0);

        Optional<RunSessionDto> result = service.updateVideoS3Key(5L, "video/new.mp4");

        assertThat(result).isEmpty();
        verify(repo, never()).findById(5L);
    }

    @Test
    void deleteCallsRepository() {
        service.delete(3L);
        verify(repo).deleteById(3L);
    }

    @Test
    void findByIdReturnsEntity() {
        RunSession e = sampleEntity(1L, 1L);
        when(repo.findById(1L)).thenReturn(Optional.of(e));

        assertThat(service.findById(1L)).contains(e);
    }
}
