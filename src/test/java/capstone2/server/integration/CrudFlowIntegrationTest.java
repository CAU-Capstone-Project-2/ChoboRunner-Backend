package capstone2.server.integration;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CrudFlowIntegrationTest {

    private static final String AUTH = "Bearer test-api-key";

    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private Long userId;
    private Long runId;

    @BeforeEach
    void seedUserAndRun() throws Exception {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", "u_" + System.nanoTime());
        user.put("password", "pw1234");
        user.put("runningLevel", "BEGINNER");
        userId = postAndGetId("/api/users", user);

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("userId", userId);
        run.put("createdDate", "2026-05-24T10:00:00");
        run.put("mode", "OUTDOOR");
        run.put("status", "DONE");
        run.put("videoS3Key", "video/k.mp4");
        run.put("duration", 1800);
        runId = postAndGetId("/api/runs", run);
    }

    private Long postAndGetId(String path, Map<String, Object> body) throws Exception {
        MvcResult r = mvc.perform(post(path).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = json.readTree(r.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    // ===== RunSession =====
    @Test
    void runSessionListIncludesSeeded() throws Exception {
        mvc.perform(get("/api/runs").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + runId + ")].userId").value(userId.intValue()));
    }

    @Test
    void runSessionFindByUserId() throws Exception {
        mvc.perform(get("/api/runs/by-user/" + userId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId));
    }

    @Test
    void runSessionUpdateAndDelete() throws Exception {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("userId", userId);
        dto.put("createdDate", "2026-05-24T11:00:00");
        dto.put("mode", "TREADMILL");
        dto.put("status", "DONE");
        dto.put("videoS3Key", "video/new.mp4");
        dto.put("duration", 2400);

        mvc.perform(put("/api/runs/" + runId).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("TREADMILL"))
                .andExpect(jsonPath("$.duration").value(2400));

        mvc.perform(delete("/api/runs/" + runId).header("Authorization", AUTH))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/runs/" + runId).header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ===== Report =====
    @Test
    void reportCreateFindUpdate() throws Exception {
        Map<String, Object> dto = Map.of("runId", runId, "totalFeedback", "ok");
        Long reportId = postAndGetId("/api/reports", dto);

        mvc.perform(get("/api/reports/" + reportId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFeedback").value("ok"));

        mvc.perform(get("/api/reports/by-run/" + runId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reportId));

        Map<String, Object> upd = Map.of("runId", runId, "totalFeedback", "updated");
        mvc.perform(put("/api/reports/" + reportId).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(upd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFeedback").value("updated"));
    }

    // ===== DetailedReport =====
    @Test
    void detailedReportCreateComputesScore() throws Exception {
        Long reportId = postAndGetId("/api/reports", Map.of("runId", runId, "totalFeedback", ""));

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("reportId", reportId);
        dto.put("type", "KNEE");
        dto.put("status", "OK");
        dto.put("measured", "90");
        dto.put("stdVal", "90");
        dto.put("refMin", 80);
        dto.put("refMax", 100);
        dto.put("sensitivity", 2.0);
        dto.put("problem", "p");
        dto.put("improved", "i");
        dto.put("summary", "s");

        mvc.perform(post("/api/detailed-reports").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100));
    }

    // ===== Feedback =====
    @Test
    void feedbackCrud() throws Exception {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("runId", runId);
        dto.put("timestamp", "00:02:15");
        dto.put("issueType", "KNEE");
        dto.put("message", "knee");
        dto.put("severity", "WARN");
        dto.put("isImproved", false);
        Long id = postAndGetId("/api/feedbacks", dto);

        mvc.perform(get("/api/feedbacks/by-run/" + runId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        dto.put("message", "knee updated");
        dto.put("isImproved", true);
        mvc.perform(put("/api/feedbacks/" + id).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("knee updated"))
                .andExpect(jsonPath("$.isImproved").value(true));

        mvc.perform(delete("/api/feedbacks/" + id).header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }

    // ===== Highlight =====
    @Test
    void highlightCrud() throws Exception {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("runId", runId);
        dto.put("startTime", "00:00:45");
        dto.put("endTime", "00:01:30");
        dto.put("issueType", "KNEE");
        dto.put("message", "highlight");
        Long id = postAndGetId("/api/highlights", dto);

        mvc.perform(get("/api/highlights/by-run/" + runId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        dto.put("message", "updated");
        mvc.perform(put("/api/highlights/" + id).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("updated"));

        mvc.perform(delete("/api/highlights/" + id).header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }
}
