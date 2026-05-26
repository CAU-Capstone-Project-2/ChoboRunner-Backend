package capstone2.server.services;

import capstone2.server.dto.ReportDto;
import capstone2.server.entities.Report;
import capstone2.server.entities.RunSession;
import capstone2.server.entities.User;
import capstone2.server.repositories.ReportRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private ReportRepository repo;
    private RunningSessionService runningSessionService;
    private ReportService service;

    @BeforeEach
    void setUp() {
        repo = mock(ReportRepository.class);
        runningSessionService = mock(RunningSessionService.class);
        service = new ReportService(repo, runningSessionService);
    }

    private RunSession run(Long id) {
        return RunSession.builder()
                .id(id)
                .user(User.builder().id(1L).username("u").password("p").runningLevel("BEGINNER").build())
                .mode("OUTDOOR").status("DONE")
                .build();
    }

    private Report entity(Long id, Long runId) {
        Report r = new Report();
        r.setId(id);
        r.setRunSession(run(runId));
        r.setTotalFeedback("good");
        return r;
    }

    @Test
    void createBindsRunSessionAndSaves() {
        when(runningSessionService.findById(5L)).thenReturn(Optional.of(run(5L)));
        when(repo.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        ReportDto dto = ReportDto.builder().runId(5L).totalFeedback("good").build();
        ReportDto result = service.create(dto);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getRunId()).isEqualTo(5L);
        assertThat(result.getTotalFeedback()).isEqualTo("good");
    }

    @Test
    void createThrowsWhenRunSessionMissing() {
        when(runningSessionService.findById(99L)).thenReturn(Optional.empty());

        ReportDto dto = ReportDto.builder().runId(99L).totalFeedback("x").build();

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("RunSession not found");
    }

    @Test
    void findDtoByIdReturnsMappedDto() {
        when(repo.findById(1L)).thenReturn(Optional.of(entity(1L, 5L)));

        Optional<ReportDto> result = service.findDtoById(1L);

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
        when(repo.findAll()).thenReturn(List.of(entity(1L, 5L), entity(2L, 5L)));

        assertThat(service.findAllDto()).hasSize(2);
    }

    @Test
    void findByRunSessionIdDtoDelegates() {
        when(repo.findByRunSessionId(5L)).thenReturn(List.of(entity(1L, 5L)));

        List<ReportDto> result = service.findByRunSessionIdDto(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRunId()).isEqualTo(5L);
    }

    @Test
    void updateAppliesFields() {
        Report existing = entity(1L, 5L);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(runningSessionService.findById(5L)).thenReturn(Optional.of(run(5L)));
        when(repo.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportDto dto = ReportDto.builder().runId(5L).totalFeedback("updated").build();
        ReportDto result = service.update(1L, dto);

        assertThat(result.getTotalFeedback()).isEqualTo("updated");
    }

    @Test
    void updateThrowsWhenReportMissing() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        ReportDto dto = ReportDto.builder().runId(5L).build();

        assertThatThrownBy(() -> service.update(99L, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Report not found");
    }

    @Test
    void deleteCallsRepository() {
        service.delete(7L);
        verify(repo).deleteById(7L);
    }
}
