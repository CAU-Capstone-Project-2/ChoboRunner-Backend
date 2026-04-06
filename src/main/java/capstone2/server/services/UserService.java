// File: `src/main/java/capstone2/server/services/UserService.java`
package capstone2.server.services;

import capstone2.server.dto.UserDto;
import capstone2.server.entities.User;
import capstone2.server.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository repo;

    public User create(User u){ return repo.save(u); }
    public UserDto create(UserDto dto){
        User user = User.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .age(dto.getAge())
                .goal(dto.getGoal())
                .height(dto.getHeight())
                .description(dto.getDescription())
                .password(dto.getPassword())
                .runningLevel(dto.getRunningLevel())
                .build();
        return toDto(repo.save(user));
    }
    public Optional<UserDto> findDtoById(Long id){ return repo.findById(id).map(this::toDto); }
    public List<UserDto> findAllDto(){ return repo.findAll().stream().map(this::toDto).toList(); }
    public UserDto update(Long id, UserDto dto){
        User user = repo.findById(id).orElseThrow();
        user.setUsername(dto.getUsername());
        user.setAge(dto.getAge());
        user.setGoal(dto.getGoal());
        user.setHeight(dto.getHeight());
        user.setDescription(dto.getDescription());
        user.setPassword(dto.getPassword());
        user.setRunningLevel(dto.getRunningLevel());
        return toDto(repo.save(user));
    }
    public void delete(Long id){ repo.deleteById(id); }

    public UserDto toDto(User user){
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .age(user.getAge())
                .goal(user.getGoal())
                .height(user.getHeight())
                .description(user.getDescription())
                .password(user.getPassword())
                .runningLevel(user.getRunningLevel())
                .build();
    }
}
