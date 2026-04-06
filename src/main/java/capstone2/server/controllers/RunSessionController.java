// File: `src/main/java/capstone2/server/controllers/RunSessionController.java`
package capstone2.server.controllers;

import capstone2.server.dto.RunSessionDto;
import capstone2.server.services.RunningSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
public class RunSessionController {
    private final RunningSessionService svc;

    @GetMapping
    public ResponseEntity<List<RunSessionDto>> all(){ return ResponseEntity.ok(svc.findAllDto()); }

    @GetMapping("/{id}")
    public ResponseEntity<RunSessionDto> get(@PathVariable Long id){
        return svc.findDtoById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<List<RunSessionDto>> byUser(@PathVariable Long userId){ return ResponseEntity.ok(svc.findByUserIdDto(userId)); }

    @PostMapping
    public ResponseEntity<RunSessionDto> create(@RequestBody RunSessionDto dto){ return ResponseEntity.ok(svc.create(dto)); }

    @PutMapping("/{id}")
    public ResponseEntity<RunSessionDto> update(@PathVariable Long id, @RequestBody RunSessionDto dto){
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
