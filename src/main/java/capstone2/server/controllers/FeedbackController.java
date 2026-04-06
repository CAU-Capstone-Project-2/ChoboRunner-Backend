// File: `src/main/java/capstone2/server/controllers/FeedbackController.java`
package capstone2.server.controllers;

import capstone2.server.dto.FeedbackLogDto;
import capstone2.server.services.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService svc;

    @GetMapping
    public ResponseEntity<List<FeedbackLogDto>> all(){ return ResponseEntity.ok(svc.findAllDto()); }

    @GetMapping("/{id}")
    public ResponseEntity<FeedbackLogDto> get(@PathVariable Long id){
        return svc.findDtoById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-run/{runId}")
    public ResponseEntity<List<FeedbackLogDto>> byRun(@PathVariable Long runId){ return ResponseEntity.ok(svc.findByRunIdDto(runId)); }

    @PostMapping
    public ResponseEntity<FeedbackLogDto> create(@RequestBody FeedbackLogDto dto){ return ResponseEntity.ok(svc.create(dto)); }

    @PutMapping("/{id}")
    public ResponseEntity<FeedbackLogDto> update(@PathVariable Long id, @RequestBody FeedbackLogDto dto){
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
