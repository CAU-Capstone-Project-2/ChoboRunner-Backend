package capstone2.server.services;

import capstone2.server.dto.AiProgressMessage;
import capstone2.server.entities.Highlight;
import capstone2.server.entities.RunSession;
import capstone2.server.repositories.HighlightRepository;
import capstone2.server.repositories.RunSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class HighlightCaptureService {

    private static final String PROGRESS_TYPE = "analysis_progress";
    private static final String WARNING_CATEGORY = "posture_warning";

    @Value("${posture.highlight.gap-threshold-sec}")
    private double gapThresholdSec;
    @Value("${posture.highlight.min-duration-sec}")
    private double minHighlightSec;
    @Value("${posture.highlight.start-padding-sec}")
    private double startPaddingSec;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "highlight-capture");
        t.setDaemon(true);
        return t;
    });

    private final HighlightRepository highlightRepository;
    private final RunSessionRepository runSessionRepository;

    private final Map<String, Map<String, OpenHighlight>> openBySession = new ConcurrentHashMap<>();

    public void onMessage(String sessionId, Long runId, String payload) {
        if (sessionId == null || runId == null || payload == null || payload.isEmpty()) {
            return;
        }
        worker.execute(() -> handle(sessionId, runId, payload));
    }

    public void flushAll(String sessionId) {
        if (sessionId == null) {
            return;
        }
        worker.execute(() -> {
            Map<String, OpenHighlight> opens = openBySession.remove(sessionId);
            if (opens == null || opens.isEmpty()) {
                return;
            }
            Long runId = opens.values().iterator().next().runId;
            RunSession session = runSessionRepository.findById(runId).orElse(null);
            if (session == null) {
                return;
            }
            for (OpenHighlight open : opens.values()) {
                persist(session, open);
            }
        });
    }

    private void handle(String sessionId, Long runId, String payload) {
        AiProgressMessage msg;
        try {
            msg = objectMapper.readValue(payload, AiProgressMessage.class);
        } catch (Exception e) {
            return;
        }

        if (!PROGRESS_TYPE.equals(msg.getType()) || msg.getElapsedSec() == null) {
            return;
        }

        double elapsedSec = msg.getElapsedSec();
        Map<String, OpenHighlight> opens =
                openBySession.computeIfAbsent(sessionId, k -> new HashMap<>());

        List<AiProgressMessage.FeedbackMessage> warnings = new ArrayList<>();
        if (msg.getFeedbackMessages() != null) {
            for (AiProgressMessage.FeedbackMessage fb : msg.getFeedbackMessages()) {
                if (fb != null
                        && WARNING_CATEGORY.equals(fb.getCategory())
                        && fb.getMetric() != null
                        && fb.getDisplayText() != null) {
                    warnings.add(fb);
                }
            }
        }

        List<OpenHighlight> toPersist = new ArrayList<>();

        for (AiProgressMessage.FeedbackMessage fb : warnings) {
            String metric = fb.getMetric();
            String text = fb.getDisplayText();
            OpenHighlight existing = opens.get(metric);
            if (existing == null) {
                opens.put(metric, new OpenHighlight(runId, metric, text, elapsedSec));
            } else if (existing.message.equals(text)) {
                existing.lastSeenSec = elapsedSec + minHighlightSec;
            } else {
                toPersist.add(existing);
                opens.put(metric, new OpenHighlight(runId, metric, text, elapsedSec));
            }
        }

        Iterator<Map.Entry<String, OpenHighlight>> it = opens.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, OpenHighlight> entry = it.next();
            OpenHighlight open = entry.getValue();
            if (elapsedSec - open.lastSeenSec > gapThresholdSec) {
                toPersist.add(open);
                it.remove();
            }
        }

        if (toPersist.isEmpty()) {
            return;
        }

        RunSession session = runSessionRepository.findById(runId).orElse(null);
        if (session == null) {
            return;
        }
        for (OpenHighlight open : toPersist) {
            persist(session, open);
        }
    }

    private void persist(RunSession session, OpenHighlight open) {
        try {
            double effectiveStart = Math.max(0.0, open.startSec - startPaddingSec);
            double effectiveEnd = open.lastSeenSec;
            if (open.lastSeenSec - open.startSec < minHighlightSec) {
                effectiveEnd += minHighlightSec;
            }
            Highlight h = Highlight.builder()
                    .runSession(session)
                    .startTime(toLocalTime(effectiveStart))
                    .endTime(toLocalTime(effectiveEnd))
                    .issueType(open.metric)
                    .message(truncate(open.message, 500))
                    .build();
            highlightRepository.save(h);
        } catch (Exception e) {
            System.err.println("[highlight] save 실패: " + e.getMessage());
        }
    }


    private static LocalTime toLocalTime(double seconds) {
        long ms = Math.max(0, Math.round(seconds * 1000.0));
        return LocalTime.ofNanoOfDay(ms * 1_000_000L);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    @PreDestroy
    void shutdown() {
        worker.shutdown();
    }

    private static class OpenHighlight {
        final Long runId;
        final String metric;
        final String message;
        final double startSec;
        double lastSeenSec;

        OpenHighlight(Long runId, String metric, String message, double elapsedSec) {
            this.runId = runId;
            this.metric = metric;
            this.message = message;
            this.startSec = elapsedSec;
            this.lastSeenSec = elapsedSec;
        }
    }
}