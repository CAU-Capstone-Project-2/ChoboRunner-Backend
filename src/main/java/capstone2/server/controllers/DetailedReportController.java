package capstone2.server.controllers;

import capstone2.server.dto.DetailedReportDto;
import capstone2.server.services.DetailedReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/detailed-reports")
@RequiredArgsConstructor
public class DetailedReportController {
    private final DetailedReportService svc;

    @GetMapping
    public ResponseEntity<List<DetailedReportDto>> all(){ return ResponseEntity.ok(svc.findAllDto()); }

    @GetMapping("/{id}")
    public ResponseEntity<DetailedReportDto> get(@PathVariable Long id){
        return svc.findDtoById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-report/{reportId}")
    public ResponseEntity<List<DetailedReportDto>> byReport(@PathVariable Long reportId){ return ResponseEntity.ok(svc.findByReportIdDto(reportId)); }

    @PostMapping
    public ResponseEntity<DetailedReportDto> create(@RequestBody DetailedReportDto d){ return ResponseEntity.ok(svc.create(d)); }

    @PutMapping("/{id}")
    public ResponseEntity<DetailedReportDto> update(@PathVariable Long id, @RequestBody DetailedReportDto d){
        if (svc.findDtoById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(svc.update(id, d));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        svc.delete(id);
        return ResponseEntity.noContent().build();
    }
}

