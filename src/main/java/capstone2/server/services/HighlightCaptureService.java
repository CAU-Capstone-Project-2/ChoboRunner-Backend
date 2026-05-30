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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

    /** 상태 변경 · 영속화의 단일 진입점 — 모든 openBySession 접근은 이 스레드에서만 */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "highlight-capture");
        t.setDaemon(true);
        return t;
    });

    /** gapThresholdSec 경과 후 gcClose 를 worker 에 위임 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "highlight-gc");
        t.setDaemon(true);
        return t;
    });

    private final HighlightRepository highlightRepository;
    private final RunSessionRepository runSessionRepository;

    private final Map<String, Map<String, OpenHighlight>> openBySession = new ConcurrentHashMap<>();

    public void onMessage(String sessionId, Long runId, String payload) {
        if (sessionId == null || runId == null || payload == null || payload.isEmpty()) return;
        worker.execute(() -> handle(sessionId, runId, payload));
    }

    /** 세션 종료 시 아직 닫히지 않은 하이라이트를 플러시한다 (안전망). */
    public void flushAll(String sessionId) {
        if (sessionId == null) return;
        worker.execute(() -> {
            Map<String, OpenHighlight> opens = openBySession.remove(sessionId);
            if (opens == null || opens.isEmpty()) return;
            Long runId = opens.values().iterator().next().runId;
            RunSession session = runSessionRepository.findById(runId).orElse(null);
            if (session == null) return;
            for (OpenHighlight open : opens.values()) {
                if (open.gcTask != null) open.gcTask.cancel(false);
                persist(session, open);
            }
        });
    }

    // ── 내부 로직 ───────────────────────────────────────────────────────────────

    private void handle(String sessionId, Long runId, String payload) {
        AiProgressMessage msg;
        try {
            msg = objectMapper.readValue(payload, AiProgressMessage.class);
        } catch (Exception e) {
            return;
        }
        if (!PROGRESS_TYPE.equals(msg.getType()) || msg.getElapsedSec() == null) return;

        double elapsedSec = msg.getElapsedSec();
        Map<String, OpenHighlight> opens = openBySession.computeIfAbsent(sessionId, k -> new HashMap<>());

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

        for (AiProgressMessage.FeedbackMessage fb : warnings) {
            String metric = fb.getMetric();
            String text = fb.getDisplayText();
            OpenHighlight existing = opens.get(metric);

            if (existing == null) {
                // 새 경고 시작
                OpenHighlight open = new OpenHighlight(runId, metric, text, elapsedSec);
                opens.put(metric, open);
                scheduleGc(sessionId, metric, open);

            } else if (existing.message.equals(text)) {
                // 동일 경고 지속: gc 재스케줄하여 gap 초기화
                existing.gcTask.cancel(false);
                existing.lastSeenSec = elapsedSec;
                scheduleGc(sessionId, metric, existing);

            } else {
                // 메시지 변경: 이전 하이라이트 즉시 닫고 새로 시작
                existing.gcTask.cancel(false);
                opens.remove(metric);
                persistOpen(existing);
                OpenHighlight open = new OpenHighlight(runId, metric, text, elapsedSec);
                opens.put(metric, open);
                scheduleGc(sessionId, metric, open);
            }
        }
        // gap 체크는 scheduleGc 가 담당 — 명시적 루프 불필요
    }

    /**
     * gapThresholdSec 후 worker 에서 gcClose 를 실행한다.
     * gcVersion 으로 재스케줄 이전의 stale close 를 방지한다.
     */
    private void scheduleGc(String sessionId, String metric, OpenHighlight open) {
        int version = ++open.gcVersion;
        long delayMs = (long)(gapThresholdSec * 1000);
        open.gcTask = scheduler.schedule(
            () -> worker.execute(() -> gcClose(sessionId, metric, open, version)),
            delayMs, TimeUnit.MILLISECONDS
        );
    }

    private void gcClose(String sessionId, String metric, OpenHighlight open, int version) {
        Map<String, OpenHighlight> opens = openBySession.get(sessionId);
        if (opens == null) return;
        // 다른 highlight 로 교체됐거나 재스케줄된 경우 무시
        if (opens.get(metric) != open || open.gcVersion != version) return;
        opens.remove(metric);
        if (opens.isEmpty()) openBySession.remove(sessionId);
        persistOpen(open);
    }

    private void persistOpen(OpenHighlight open) {
        RunSession session = runSessionRepository.findById(open.runId).orElse(null);
        if (session == null) return;
        persist(session, open);
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
        scheduler.shutdown();
    }

    private static class OpenHighlight {
        final Long runId;
        final String metric;
        final String message;
        final double startSec;
        double lastSeenSec;
        int gcVersion = 0;
        ScheduledFuture<?> gcTask;

        OpenHighlight(Long runId, String metric, String message, double elapsedSec) {
            this.runId = runId;
            this.metric = metric;
            this.message = message;
            this.startSec = elapsedSec;
            this.lastSeenSec = elapsedSec;
        }
    }
}