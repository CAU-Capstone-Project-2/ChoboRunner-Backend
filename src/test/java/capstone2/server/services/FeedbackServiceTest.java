package capstone2.server.services;

import capstone2.server.dto.FeedbackLogDto;
import capstone2.server.entities.FeedbackLog;
import capstone2.server.entities.RunSession;
import capstone2.server.repositories.FeedbackLogRepository;
import capstone2.server.repositories.RunSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceTest {

    private FeedbackLogRepository repo;
    private RunSessionRepository runRepo;
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        repo = mock(FeedbackLogRepository.class);
        runRepo = mock(RunSessionRepository.class);
        service = new FeedbackService(repo, runRepo);
    }

    private RunSession run(Long id) {
        return RunSession.builder().id(id).mode("OUTDOOR").status("DONE").build();
    }

    private FeedbackLogDto sampleDto() {
        return FeedbackLogDto.builder()
                .runId(5L)
                .timestamp(LocalTime.of(0, 2, 15))
                .issueType("KNEE")
                .message("knee angle off")
                .severity("WARN")
                .isImproved(false)
                .build();
    }

    private FeedbackLog entity(Long id) {
        return FeedbackLog.builder()
                .id(id)
                .runSession(run(5L))
                .timestamp(LocalTime.of(0, 2, 15))
                .issueType("KNEE")
                .message("knee angle off")
                .severity("WARN")
                .isImproved(false)
                .build();
    }

    @Test
    void createBindsRunSessionAndPersists() {
        when(runRepo.getReferenceById(5L)).thenReturn(run(5L));
        when(repo.save(any(FeedbackLog.class))).thenAnswer(inv -> {
            FeedbackLog f = inv.getArgument(0);
            f.setId(100L);
            return f;
        });

        FeedbackLogDto result = service.create(sampleDto());

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getRunId()).isEqualTo(5L);
        assertThat(result.getIssueType()).isEqualTo("KNEE");
    }

    @Test
    void findDtoByIdReturnsMappedDto() {
        when(repo.findById(1L)).thenReturn(Optional.of(entity(1L)));

        Optional<FeedbackLogDto> result = service.findDtoById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getRunId()).isEqualTo(5L);
    }

    @Test
    void findDtoByIdReturnsEmptyWhenMissing() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.findDtoById(99L)).isEmpty();
    }

    @Test
    void findAllDtoMapsAll() {
        when(repo.findAll()).thenReturn(List.of(entity(1L), entity(2L)));

        assertThat(service.findAllDto()).hasSize(2);
    }

    @Test
    void findByRunIdDtoDelegates() {
        when(repo.findByRunSessionId(5L)).thenReturn(List.of(entity(1L)));

        assertThat(service.findByRunIdDto(5L)).hasSize(1);
    }

    @Test
    void updateOverwritesFields() {
        FeedbackLog existing = entity(7L);
        when(repo.findById(7L)).thenReturn(Optional.of(existing));
        when(runRepo.getReferenceById(5L)).thenReturn(run(5L));
        when(repo.save(any(FeedbackLog.class))).thenAnswer(inv -> inv.getArgument(0));

        FeedbackLogDto dto = sampleDto();
        dto.setMessage("updated msg");
        dto.setIsImproved(true);

        FeedbackLogDto result = service.update(7L, dto);

        assertThat(result.getMessage()).isEqualTo("updated msg");
        assertThat(result.getIsImproved()).isTrue();
    }

    @Test
    void updateThrowsWhenMissing() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, sampleDto()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteCallsRepository() {
        service.delete(3L);
        verify(repo).deleteById(3L);
    }
}
