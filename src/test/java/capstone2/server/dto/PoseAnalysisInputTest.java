package capstone2.server.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PoseAnalysisInputTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesAiServerResponseSample() throws Exception {
        String json = """
                {
                  "schema_version": "1.1",
                  "reference_feedback_only": true,
                  "ic_candidate_frames": [23, 46, 69],
                  "ic_selection": { "selected_ic_count": 4 },
                  "metrics": [
                    {
                      "metric_name": "trunk_lean",
                      "tier": "primary",
                      "experimental": false,
                      "value": 89.86,
                      "unit": "degree",
                      "category": "likely_artifact_or_extreme_pose",
                      "interpretation": "상체 기울기 각도가 통상 범위를 벗어남"
                    },
                    {
                      "metric_name": "initial_knee_flexion",
                      "tier": "primary",
                      "experimental": false,
                      "value": 80.78,
                      "category": "likely_artifact_knee_angle",
                      "interpretation": "굴곡이 큼"
                    },
                    {
                      "metric_name": "foot_strike_pattern",
                      "tier": "secondary",
                      "experimental": true,
                      "value": 130.34,
                      "pattern_tendency": "unclear",
                      "category": "unclear",
                      "interpretation": "참고 각도 구간에 명확히 들어맞지 않음"
                    }
                  ],
                  "overlay_s3_url": "https://bucket.s3.ap-northeast-2.amazonaws.com/analysis_results/abc/overlay.mp4",
                  "run_id": "abc"
                }
                """;

        PoseAnalysisInput input = mapper.readValue(json, PoseAnalysisInput.class);

        assertEquals("abc", input.runId());
        assertEquals("https://bucket.s3.ap-northeast-2.amazonaws.com/analysis_results/abc/overlay.mp4",
                input.overlayS3Url());
        assertEquals(3, input.metrics().size());

        PoseAnalysisInput.Metric trunk = input.metrics().get(0);
        assertEquals("trunk_lean", trunk.metricName());
        assertEquals("primary", trunk.tier());
        assertFalse(trunk.experimental());
        assertEquals(89.86, trunk.value());
        assertEquals("likely_artifact_or_extreme_pose", trunk.category());
        assertNull(trunk.patternTendency());

        PoseAnalysisInput.Metric foot = input.metrics().get(2);
        assertEquals("foot_strike_pattern", foot.metricName());
        assertEquals("unclear", foot.patternTendency());
        assertTrue(foot.experimental());
    }

    @Test
    void ignoresUnknownTopLevelFields() throws Exception {
        String json = """
                {
                  "schema_version": "1.1",
                  "stance_side_resolved": "right",
                  "ic_representative_frame": 324,
                  "metrics": [],
                  "overlay_s3_url": "https://example.com/overlay.mp4",
                  "run_id": "x"
                }
                """;

        PoseAnalysisInput input = mapper.readValue(json, PoseAnalysisInput.class);
        assertEquals("x", input.runId());
        assertTrue(input.metrics().isEmpty());
    }
}
