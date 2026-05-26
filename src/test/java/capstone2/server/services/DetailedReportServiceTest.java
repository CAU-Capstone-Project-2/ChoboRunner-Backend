package capstone2.server.services;

import capstone2.server.dto.DetailedReportDto;
import capstone2.server.entities.DetailedReport;
import capstone2.server.entities.Report;
import capstone2.server.repositories.DetailedReportRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetailedReportServiceTest {

    private DetailedReportRepository repo;
    private EntityManager em;
    private DetailedReportService service;

    @BeforeEach
    void setUp() {
        repo = mock(DetailedReportRepository.class);
        em = mock(EntityManager.class);
        service = new DetailedReportService(repo, em);
    }

    private DetailedReportDto baseDto() {
        return DetailedReportDto.builder()
                .reportId(1L)
                .type("KNEE_ANGLE")
                .status("WARN")
                .measured("100")
                .stdVal("90")
                .refMin(80)
                .refMax(100)
                .sensitivity(2.0)
                .problem("p").improved("i").summary("s")
                .build();
    }

    private DetailedReport baseEntity(Long id) {
        DetailedReport d = new DetailedReport();
        d.setId(id);
        Report r = new Report();
        r.setId(1L);
        d.setReport(r);
        d.setType("KNEE_ANGLE");
        d.setStatus("OK");
        d.setMeasured("100");
        d.setStdVal("90");
        d.setRefMin(80);
        d.setRefMax(100);
        d.setSensitivity(2.0);
        return d;
    }

    @Test
    void createComputesScore100WhenWithinRange() {
        Report ref = new Report(); ref.setId(1L);
        when(em.getReference(eq(Report.class), eq(1L))).thenReturn(ref);
        when(repo.save(any(DetailedReport.class))).thenAnswer(inv -> {
            DetailedReport d = inv.getArgument(0);
            d.setId(10L);
            return d;
        });

        DetailedReportDto result = service.create(baseDto());

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getScore()).isEqualTo(100);
    }

    @Test
    void createReturnsNullScoreWhenMeasuredNotNumeric() {
        Report ref = new Report(); ref.setId(1L);
        when(em.getReference(eq(Report.class), eq(1L))).thenReturn(ref);
        when(repo.save(any(DetailedReport.class))).thenAnswer(inv -> inv.getArgument(0));

        DetailedReportDto dto = baseDto();
        dto.setMeasured("not-a-number");

        DetailedReportDto result = service.create(dto);

        assertThat(result.getScore()).isNull();
    }

    @Test
    void createReturnsNullScoreWhenRefMinGreaterThanRefMax() {
        Report ref = new Report(); ref.setId(1L);
        when(em.getReference(eq(Report.class), eq(1L))).thenReturn(ref);
        when(repo.save(any(DetailedReport.class))).thenAnswer(inv -> inv.getArgument(0));

        DetailedReportDto dto = baseDto();
        dto.setRefMin(200);
        dto.setRefMax(100);
        dto.setMeasured("150");

        DetailedReportDto result = service.create(dto);

        assertThat(result.getScore()).isNull();
    }

    @Test
    void createScoresWithinFivePercentToleranceAs100() {
        Report ref = new Report(); ref.setId(1L);
        when(em.getReference(eq(Report.class), eq(1L))).thenReturn(ref);
        when(repo.save(any(DetailedReport.class))).thenAnswer(inv -> inv.getArgument(0));

        DetailedReportDto dto = baseDto();
        dto.setRefMin(80);
        dto.setRefMax(100);
        dto.setMeasured("103"); // 3% over refMax → within 5% tolerance

        DetailedReportDto result = service.create(dto);

        assertThat(result.getScore()).isEqualTo(100);
    }

    @Test
    void createScoresLargeErrorBelow100() {
        Report ref = new Report(); ref.setId(1L);
        when(em.getReference(eq(Report.class), eq(1L))).thenReturn(ref);
        when(repo.save(any(DetailedReport.class))).thenAnswer(inv -> inv.getArgument(0));

        DetailedReportDto dto = baseDto();
        dto.setRefMin(80);
        dto.setRefMax(100);
        dto.setMeasured("150"); // 50% over → large error → expect significantly < 100
        dto.setSensitivity(2.0);

        DetailedReportDto result = service.create(dto);

        assertThat(result.getScore()).isNotNull();
        assertThat(result.getScore()).isLessThan(80).isGreaterThanOrEqualTo(0);
    }

    @Test
    void createDefaultsSensitivityWhenZero() {
        Report ref = new Report(); ref.setId(1L);
        when(em.getReference(eq(Report.class), eq(1L))).thenReturn(ref);
        when(repo.save(any(DetailedReport.class))).thenAnswer(inv -> inv.getArgument(0));

        DetailedReportDto dto = baseDto();
        dto.setSensitivity(0.0); // null-safe applyDtoToEntity stores 0; service should fall back to 2.0
        dto.setRefMin(80);
        dto.setRefMax(100);
        dto.setMeasured("150");

        DetailedReportDto result = service.create(dto);

        assertThat(result.getScore()).isNotNull();
    }

    @Test
    void createReturnsNullScoreWhenMeasuredNull() {
        Report ref = new Report(); ref.setId(1L);
        when(em.getReference(eq(Report.class), eq(1L))).thenReturn(ref);
        when(repo.save(any(DetailedReport.class))).thenAnswer(inv -> inv.getArgument(0));

        DetailedReportDto dto = baseDto();
        dto.setMeasured(null);

        DetailedReportDto result = service.create(dto);

        assertThat(result.getScore()).isNull();
    }

    @Test
    void findDtoByIdReturnsMappedDto() {
        when(repo.findById(1L)).thenReturn(Optional.of(baseEntity(1L)));

        Optional<DetailedReportDto> result = service.findDtoById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getReportId()).isEqualTo(1L);
    }

    @Test
    void findAllDtoMapsAll() {
        when(repo.findAll()).thenReturn(List.of(baseEntity(1L), baseEntity(2L)));

        assertThat(service.findAllDto()).hasSize(2);
    }

    @Test
    void findByReportIdDtoDelegates() {
        when(repo.findByReportId(1L)).thenReturn(List.of(baseEntity(1L)));

        assertThat(service.findByReportIdDto(1L)).hasSize(1);
    }

    @Test
    void updateAppliesFieldsAndRecomputesScore() {
        DetailedReport existing = baseEntity(5L);
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        Report ref = new Report(); ref.setId(1L);
        when(em.getReference(eq(Report.class), eq(1L))).thenReturn(ref);
        when(repo.save(any(DetailedReport.class))).thenAnswer(inv -> inv.getArgument(0));

        DetailedReportDto dto = baseDto();
        dto.setMeasured("85"); // within 80~100 → score 100

        DetailedReportDto result = service.update(5L, dto);

        assertThat(result.getScore()).isEqualTo(100);
        assertThat(result.getMeasured()).isEqualTo("85");
    }

    @Test
    void updateThrowsWhenMissing() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, baseDto()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("DetailedReport not found");
    }

    @Test
    void deleteCallsRepository() {
        service.delete(3L);
        verify(repo).deleteById(3L);
    }
}
