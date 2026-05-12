package capstone2.server.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PostureAnalysisResponse(
        Long reportId,
        Long runSessionId,
        String overlayS3Url,
        String totalFeedback,
        List<DetailedReportDto> details
) {
}
