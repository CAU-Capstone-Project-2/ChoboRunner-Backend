package capstone2.server.controllers;

import capstone2.server.dto.ReportDto;
import capstone2.server.services.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService svc;

    @GetMapping
    public ResponseEntity<List<ReportDto>> all(){ return ResponseEntity.ok(svc.findAllDto()); }

    @GetMapping("/{id}")
    public ResponseEntity<ReportDto> get(@PathVariable Long id){
        return svc.findDtoById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ReportDto> create(@RequestBody ReportDto d){ return ResponseEntity.ok(svc.create(d)); }

    @PutMapping("/{id}")
    public ResponseEntity<ReportDto> update(@PathVariable Long id, @RequestBody ReportDto d){
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
