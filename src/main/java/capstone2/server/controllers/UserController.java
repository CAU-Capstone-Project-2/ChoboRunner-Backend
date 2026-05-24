// File: `src/main/java/capstone2/server/controllers/UserController.java`
package capstone2.server.controllers;

import capstone2.server.dto.LoginRequest;
import capstone2.server.dto.UserDto;
import capstone2.server.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService svc;

    @GetMapping
    public ResponseEntity<List<UserDto>> all(){ return ResponseEntity.ok(svc.findAllDto()); }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> get(@PathVariable Long id){
        return svc.findDtoById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody UserDto dto){ return ResponseEntity.ok(svc.create(dto)); }

    @PostMapping("/login")
    public ResponseEntity<UserDto> login(@RequestBody LoginRequest req){
        return svc.login(req.username(), req.password())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> update(@PathVariable Long id, @RequestBody UserDto dto){
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
