package capstone2.server.services;

import capstone2.server.dto.UserDto;
import capstone2.server.entities.User;
import capstone2.server.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceLoginTest {

    private UserRepository repo;
    private UserService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepository.class);
        service = new UserService(repo);
    }

    @Test
    void loginReturnsDtoWhenCredentialsMatch() {
        String rawPassword = "secret-pw";
        User stored = User.builder()
                .id(1L)
                .username("runner")
                .password(new BCryptPasswordEncoder().encode(rawPassword))
                .runningLevel("BEGINNER")
                .age(28)
                .height(175)
                .build();
        when(repo.findByUsername("runner")).thenReturn(Optional.of(stored));

        Optional<UserDto> result = service.login("runner", rawPassword);

        assertThat(result).isPresent();
        UserDto dto = result.get();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getUsername()).isEqualTo("runner");
        assertThat(dto.getRunningLevel()).isEqualTo("BEGINNER");
        assertThat(dto.getPassword()).isNull();
    }

    @Test
    void loginReturnsEmptyWhenUsernameNotFound() {
        when(repo.findByUsername("ghost")).thenReturn(Optional.empty());

        Optional<UserDto> result = service.login("ghost", "anything");

        assertThat(result).isEmpty();
        verify(repo).findByUsername("ghost");
    }

    @Test
    void loginReturnsEmptyWhenPasswordDoesNotMatch() {
        User stored = User.builder()
                .id(2L)
                .username("runner")
                .password(new BCryptPasswordEncoder().encode("correct-pw"))
                .runningLevel("INTERMEDIATE")
                .build();
        when(repo.findByUsername("runner")).thenReturn(Optional.of(stored));

        Optional<UserDto> result = service.login("runner", "wrong-pw");

        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void loginThrowsWhenUsernameIsBlank(String username) {
        assertThatThrownBy(() -> service.login(username, "any-pw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");

        verifyNoInteractions(repo);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void loginThrowsWhenPasswordIsBlank(String password) {
        assertThatThrownBy(() -> service.login("runner", password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");

        verifyNoInteractions(repo);
    }

    @Test
    void loginDoesNotCallRepositoryWhenInputInvalid() {
        assertThatThrownBy(() -> service.login(null, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repo, org.mockito.Mockito.never()).findByUsername(anyString());
    }
}
