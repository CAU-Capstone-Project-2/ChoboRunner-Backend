package capstone2.server.services;

import capstone2.server.dto.DetailedReportDto;
import capstone2.server.entities.DetailedReport;
import capstone2.server.entities.Report;
import capstone2.server.repositories.DetailedReportRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class DetailedReportService {
    private final DetailedReportRepository repo;
    private final EntityManager em;

    // DTO 기반 생성: DTO -> Entity 매핑, score 계산 후 저장
    public DetailedReportDto create(DetailedReportDto dto) {
        DetailedReport d = toEntity(dto);
        d.setScore(calculatePostureScoreNullable(d));
        return toDto(repo.save(d));
    }

    public Optional<DetailedReportDto> findDtoById(Long id) {
        return repo.findById(id).map(this::toDto);
    }

    public List<DetailedReportDto> findAllDto() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    public List<DetailedReportDto> findByReportIdDto(Long reportId) {
        return repo.findByReportId(reportId).stream().map(this::toDto).toList();
    }

    // DTO 기반 업데이트: 기존 엔티티 로드 후 필드 덮어쓰기, score 재계산
    public DetailedReportDto update(Long id, DetailedReportDto dto){
        DetailedReport existing = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("DetailedReport not found: " + id));
        applyDtoToEntity(dto, existing);
        existing.setScore(calculatePostureScoreNullable(existing));
        return toDto(repo.save(existing));
    }

    public void delete(Long id){ repo.deleteById(id); }

    // DTO -> Entity 변환 (새 엔티티 생성)
    private DetailedReport toEntity(DetailedReportDto dto){
        DetailedReport d = new DetailedReport();
        applyDtoToEntity(dto, d);
        return d;
    }

    private DetailedReportDto toDto(DetailedReport d) {
        return DetailedReportDto.builder()
                .id(d.getId())
                .reportId(d.getReport() != null ? d.getReport().getId() : null)
                .type(d.getType())
                .status(d.getStatus())
                .measured(d.getMeasured())
                .stdVal(d.getStdVal())
                .refMin(d.getRefMin())
                .refMax(d.getRefMax())
                .sensitivity(d.getSensitivity())
                .problem(d.getProblem())
                .improved(d.getImproved())
                .summary(d.getSummary())
                .score(d.getScore())
                .build();
    }

    // DTO 값을 이미 존재하는 엔티티에 적용
    private void applyDtoToEntity(DetailedReportDto dto, DetailedReport d){
        if (dto.getReportId() != null){
            // 연관된 Report를 레퍼런스로 설정 (지연 로드, DB 조회 없음)
            Report reportRef = em.getReference(Report.class, dto.getReportId());
            d.setReport(reportRef);
        }
        d.setType(dto.getType());
        d.setStatus(dto.getStatus());
        d.setMeasured(dto.getMeasured());
        d.setStdVal(dto.getStdVal());
        d.setRefMin(dto.getRefMin() != null ? dto.getRefMin() : 0);
        d.setRefMax(dto.getRefMax() != null ? dto.getRefMax() : 0);
        d.setSensitivity(dto.getSensitivity() != null ? dto.getSensitivity() : 0);
        d.setProblem(dto.getProblem());
        d.setImproved(dto.getImproved());
        d.setSummary(dto.getSummary());
    }

    // 제공된 파이썬 식을 자바로 이식한 지수 감쇠 모델
    private Integer calculatePostureScoreNullable(DetailedReport d){
        String measuredStr = d.getMeasured();
        int refMin = d.getRefMin();
        int refMax = d.getRefMax();
        double k1 = d.getSensitivity() != 0 ? d.getSensitivity() : 2.0; // 감쇠 상수

        if (measuredStr == null) return null;
        double measuredAngle;
        try {
            measuredAngle = Double.parseDouble(measuredStr);
        } catch (NumberFormatException e) {
            return null;
        }

        if (refMin > refMax) return null;
        // 정상 범위 체크
        System.out.println("Measured: " + measuredAngle + ", RefMin: " + refMin + ", RefMax: " + refMax);
        if (refMin <= measuredAngle && measuredAngle <= refMax) return 100;

        double refVal = measuredAngle < refMin ? refMin : refMax;
        if (refVal == 0) return null; // 분모 방지

        double error = Math.abs((measuredAngle - refVal) / refVal);

        System.out.println("E: " + error);
        // 오차가 5% 이하인 경우 보정
        if (error <= 0.05) return 100;
        double score = 100.0 * Math.exp(-k1 * (error - 0.05));
        return (int) Math.round(Math.max(0.0, score));
    }
}
