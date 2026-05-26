package capstone2.server.services;

import capstone2.server.dto.HighlightDto;
import capstone2.server.entities.Highlight;
import capstone2.server.entities.RunSession;
import capstone2.server.repositories.HighlightRepository;
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

class HighlightServiceTest {

    private HighlightRepository repo;
    private RunSessionRepository runRepo;
    private HighlightService service;

    @BeforeEach
    void setUp() {
        repo = mock(HighlightRepository.class);
        runRepo = mock(RunSessionRepository.class);
        service = new HighlightService(repo, runRepo);
    }

    private RunSession run(Long id) {
        return RunSession.builder().id(id).mode("OUTDOOR").status("DONE").build();
    }

    private HighlightDto sampleDto() {
        return HighlightDto.builder()
                .runId(5L)
                .startTime(LocalTime.of(0, 0, 45))
                .endTime(LocalTime.of(0, 1, 30))
                .issueType("KNEE")
                .message("knee form issue")
                .build();
    }

    private Highlight entity(Long id) {
        return Highlight.builder()
                .id(id)
                .runSession(run(5L))
                .startTime(LocalTime.of(0, 0, 45))
                .endTime(LocalTime.of(0, 1, 30))
                .issueType("KNEE")
                .message("knee form issue")
                .build();
    }

    @Test
    void createBindsRunSessionAndPersists() {
        when(runRepo.findById(5L)).thenReturn(Optional.of(run(5L)));
        when(repo.save(any(Highlight.class))).thenAnswer(inv -> {
            Highlight h = inv.getArgument(0);
            h.setId(100L);
            return h;
        });

        HighlightDto result = service.create(sampleDto());

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getRunId()).isEqualTo(5L);
        assertThat(result.getIssueType()).isEqualTo("KNEE");
    }

    @Test
    void createThrowsWhenRunSessionMissing() {
        when(runRepo.findById(99L)).thenReturn(Optional.empty());

        HighlightDto dto = sampleDto();
        dto.setRunId(99L);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void findDtoByIdReturnsMappedDto() {
        when(repo.findById(1L)).thenReturn(Optional.of(entity(1L)));

        Optional<HighlightDto> result = service.findDtoById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getRunId()).isEqualTo(5L);
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
        Highlight existing = entity(7L);
        when(repo.findById(7L)).thenReturn(Optional.of(existing));
        when(runRepo.findById(5L)).thenReturn(Optional.of(run(5L)));
        when(repo.save(any(Highlight.class))).thenAnswer(inv -> inv.getArgument(0));

        HighlightDto dto = sampleDto();
        dto.setMessage("updated");

        HighlightDto result = service.update(7L, dto);

        assertThat(result.getMessage()).isEqualTo("updated");
    }

    @Test
    void updateThrowsWhenHighlightMissing() {
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
