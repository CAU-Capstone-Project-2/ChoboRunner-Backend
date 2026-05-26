package capstone2.server.services;

import capstone2.server.dto.UserDto;
import capstone2.server.entities.User;
import capstone2.server.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository repo;
    private UserService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepository.class);
        service = new UserService(repo);
    }

    private UserDto sampleDto() {
        return UserDto.builder()
                .username("runner")
                .password("pw1234")
                .runningLevel("BEGINNER")
                .age(28)
                .height(175)
                .description("desc")
                .goal("5km")
                .build();
    }

    private User sampleEntity(Long id) {
        return User.builder()
                .id(id)
                .username("runner")
                .password(new BCryptPasswordEncoder().encode("pw1234"))
                .runningLevel("BEGINNER")
                .age(28)
                .height(175)
                .description("desc")
                .goal("5km")
                .build();
    }

    @Test
    void createPersistsHashedPasswordAndReturnsDto() {
        when(repo.existsByUsername("runner")).thenReturn(false);
        when(repo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserDto result = service.create(sampleDto());

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getUsername()).isEqualTo("runner");
        assertThat(result.getPassword()).isNull();
        verify(repo).save(any(User.class));
    }

    @Test
    void createThrowsWhenUsernameAlreadyExists() {
        when(repo.existsByUsername("runner")).thenReturn(true);

        assertThatThrownBy(() -> service.create(sampleDto()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 username");

        verify(repo, never()).save(any());
    }

    @Test
    void createThrowsWhenUsernameMissing() {
        UserDto dto = sampleDto();
        dto.setUsername("  ");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }

    @Test
    void createThrowsWhenPasswordMissing() {
        UserDto dto = sampleDto();
        dto.setPassword(null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    @Test
    void createThrowsWhenRunningLevelMissing() {
        UserDto dto = sampleDto();
        dto.setRunningLevel("");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runningLevel");
    }

    @Test
    void findDtoByIdReturnsMappedDto() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleEntity(1L)));

        Optional<UserDto> result = service.findDtoById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        assertThat(result.get().getPassword()).isNull();
    }

    @Test
    void findDtoByIdReturnsEmptyWhenMissing() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.findDtoById(99L)).isEmpty();
    }

    @Test
    void findAllDtoReturnsList() {
        when(repo.findAll()).thenReturn(List.of(sampleEntity(1L), sampleEntity(2L)));

        List<UserDto> result = service.findAllDto();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserDto::getId).containsExactly(1L, 2L);
    }

    @Test
    void updateChangesFieldsAndRehashesPasswordWhenProvided() {
        User existing = sampleEntity(5L);
        String originalHash = existing.getPassword();
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto dto = sampleDto();
        dto.setPassword("new-pw");
        dto.setAge(30);

        UserDto result = service.update(5L, dto);

        assertThat(result.getAge()).isEqualTo(30);
        assertThat(existing.getPassword()).isNotEqualTo(originalHash);
        assertThat(new BCryptPasswordEncoder().matches("new-pw", existing.getPassword())).isTrue();
    }

    @Test
    void updateKeepsPasswordWhenDtoPasswordBlank() {
        User existing = sampleEntity(5L);
        String originalHash = existing.getPassword();
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto dto = sampleDto();
        dto.setPassword("");

        service.update(5L, dto);

        assertThat(existing.getPassword()).isEqualTo(originalHash);
    }

    @Test
    void updateThrowsWhenChangingToTakenUsername() {
        User existing = sampleEntity(5L);
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(repo.existsByUsername("taken")).thenReturn(true);

        UserDto dto = sampleDto();
        dto.setUsername("taken");

        assertThatThrownBy(() -> service.update(5L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 username");
    }

    @Test
    void updateAllowsKeepingSameUsername() {
        User existing = sampleEntity(5L);
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto dto = sampleDto();

        UserDto result = service.update(5L, dto);

        assertThat(result.getUsername()).isEqualTo("runner");
        verify(repo, never()).existsByUsername(any());
    }

    @Test
    void deleteCallsRepository() {
        service.delete(7L);
        verify(repo).deleteById(7L);
    }
}
