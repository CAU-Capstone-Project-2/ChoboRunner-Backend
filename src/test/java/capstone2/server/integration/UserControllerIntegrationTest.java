package capstone2.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerIntegrationTest {

    private static final String AUTH = "Bearer test-api-key";

    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String username;
    private Map<String, Object> userBody;

    @BeforeEach
    void setUp() {
        username = "runner_" + System.nanoTime();
        userBody = new LinkedHashMap<>();
        userBody.put("username", username);
        userBody.put("password", "pw1234");
        userBody.put("runningLevel", "BEGINNER");
        userBody.put("age", 28);
        userBody.put("height", 175);
        userBody.put("description", "desc");
        userBody.put("goal", "5km");
    }

    private String body(Map<String, Object> m) throws Exception { return json.writeValueAsString(m); }

    @Test
    void missingAuthHeaderReturns401() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void wrongAuthHeaderReturns401() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Bearer wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createUserPersistsAndHidesPassword() throws Exception {
        mvc.perform(post("/api/users")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(userBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void duplicateUsernameReturns400WithMessage() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(body(userBody)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/users").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(body(userBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("이미 사용 중인 username")));
    }

    @Test
    void missingRequiredFieldReturns400() throws Exception {
        userBody.put("username", "");
        mvc.perform(post("/api/users").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(body(userBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("username")));
    }

    @Test
    void loginReturnsUserDtoWhenCredentialsMatch() throws Exception {
        MvcResult created = mvc.perform(post("/api/users").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(body(userBody)))
                .andExpect(status().isOk()).andReturn();
        Long createdId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String login = body(Map.of("username", username, "password", "pw1234"));
        mvc.perform(post("/api/users/login").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void loginReturns401WhenPasswordWrong() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(body(userBody)))
                .andExpect(status().isOk());

        String login = body(Map.of("username", username, "password", "wrong-pw"));
        mvc.perform(post("/api/users/login").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturns401WhenUserMissing() throws Exception {
        String login = body(Map.of("username", "ghost", "password", "any"));
        mvc.perform(post("/api/users/login").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturns400WhenUsernameBlank() throws Exception {
        String login = body(Map.of("username", "", "password", "pw"));
        mvc.perform(post("/api/users/login").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("필수")));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        mvc.perform(get("/api/users/9999999").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthCheckIsWhitelisted() throws Exception {
        MvcResult result = mvc.perform(get("/healthcheck")).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("OK");
    }
}
