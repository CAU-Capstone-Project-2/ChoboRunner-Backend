// File: `src/main/java/capstone2/server/controllers/HighlightController.java`
package capstone2.server.controllers;

import capstone2.server.dto.HighlightDto;
import capstone2.server.services.HighlightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/highlights")
@RequiredArgsConstructor
public class HighlightController {
    private final HighlightService svc;

    @GetMapping
    public ResponseEntity<List<HighlightDto>> all(){ return ResponseEntity.ok(svc.findAllDto()); }

    @GetMapping("/{id}")
    public ResponseEntity<HighlightDto> get(@PathVariable Long id){
        return svc.findDtoById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-run/{runId}")
    public ResponseEntity<List<HighlightDto>> byRun(@PathVariable Long runId){ return ResponseEntity.ok(svc.findByRunIdDto(runId)); }

    @PostMapping
    public ResponseEntity<HighlightDto> create(@RequestBody HighlightDto dto){ return ResponseEntity.ok(svc.create(dto)); }

    @PutMapping("/{id}")
    public ResponseEntity<HighlightDto> update(@PathVariable Long id, @RequestBody HighlightDto dto){
        if (svc.findDtoById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(svc.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        svc.delete(id);
        return ResponseEntity.noContent().build();
    }
}
