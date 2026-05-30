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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI analysis_progress 의 posture_warning 을 하이라이트로 적재한다.
 *
 * <p>설계: 런타임에는 타이밍 로직 없이 경고 메시지마다 곧바로 하이라이트 한 건을 저장하고,
 * 세션 종료(flushAll) 시 후처리로 인접한 동일 경고를 병합한다.
 * <ul>
 *   <li>적재: 각 경고 → [max(0, elapsed - startPadding), elapsed + minDuration] 구간으로 저장</li>
 *   <li>병합: start 오름차순으로 훑어 직전 생존 하이라이트와 type·message 가 같고
 *       {@code prev.end + gapThreshold > curr.start} 면 뒤 하이라이트를 지우고 앞 end 를 연장</li>
 * </ul>
 */
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

    /** 상태 변경 · 영속화의 단일 진입점 — 적재와 병합을 순서대로 직렬화한다 */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "highlight-capture");
        t.setDaemon(true);
        return t;
    });

    private final HighlightRepository highlightRepository;
    private final RunSessionRepository runSessionRepository;

    /** flushAll 에서 병합 대상 runId 를 찾기 위한 sessionId → runId 매핑 */
    private final Map<String, Long> runIdBySession = new ConcurrentHashMap<>();

    public void onMessage(String sessionId, Long runId, String payload) {
        if (sessionId == null || runId == null || payload == null || payload.isEmpty()) return;
        worker.execute(() -> handle(sessionId, runId, payload));
    }

    /** 세션 종료 시 적재된 하이라이트를 후처리로 병합한다. */
    public void flushAll(String sessionId) {
        if (sessionId == null) return;
        worker.execute(() -> {
            Long runId = runIdBySession.remove(sessionId);
            if (runId == null) return;
            mergeRun(runId);
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
        runIdBySession.put(sessionId, runId);

        if (msg.getFeedbackMessages() == null) return;

        RunSession session = null;
        for (AiProgressMessage.FeedbackMessage fb : msg.getFeedbackMessages()) {
            if (fb == null
                    || !WARNING_CATEGORY.equals(fb.getCategory())
                    || fb.getMetric() == null
                    || fb.getDisplayText() == null) {
                continue;
            }
            if (session == null) {
                session = runSessionRepository.findById(runId).orElse(null);
                if (session == null) return;
            }
            persist(session, fb.getMetric(), fb.getDisplayText(), elapsedSec);
        }
    }

    /** 경고 한 건을 [max(0, elapsed - startPadding), elapsed + minDuration] 구간으로 저장한다. */
    private void persist(RunSession session, String metric, String text, double elapsedSec) {
        try {
            double start = Math.max(0.0, elapsedSec - startPaddingSec);
            double end = elapsedSec + minHighlightSec;
            Highlight h = Highlight.builder()
                    .runSession(session)
                    .startTime(toLocalTime(start))
                    .endTime(toLocalTime(end))
                    .issueType(metric)
                    .message(truncate(text, 500))
                    .build();
            highlightRepository.save(h);
        } catch (Exception e) {
            System.err.println("[highlight] save 실패: " + e.getMessage());
        }
    }

    /**
     * 적재된 하이라이트를 후처리 병합한다.
     *
     * <p>start 오름차순으로 훑으며 직전 생존 하이라이트(prev)와 type·message 가 같고
     * {@code prev.end + gapThreshold > curr.start} 면 같은 구간으로 보고 curr 를 삭제한 뒤
     * prev 의 end 를 더 늦은 쪽으로 연장한다.
     */
    private void mergeRun(Long runId) {
        List<Highlight> all = highlightRepository.findByRunSessionId(runId);
        if (all.size() < 2) return;
        all.sort(Comparator.comparing(Highlight::getStartTime));

        List<Highlight> toDelete = new ArrayList<>();
        Highlight prev = null;
        for (Highlight curr : all) {
            if (prev != null
                    && prev.getIssueType().equals(curr.getIssueType())
                    && prev.getMessage().equals(curr.getMessage())
                    && seconds(prev.getEndTime()) + gapThresholdSec > seconds(curr.getStartTime())) {
                // 동일 경고 인접: 뒤 하이라이트 삭제, 앞 end 연장
                if (seconds(curr.getEndTime()) > seconds(prev.getEndTime())) {
                    prev.setEndTime(curr.getEndTime());
                    highlightRepository.save(prev);
                }
                toDelete.add(curr);
            } else {
                prev = curr;
            }
        }
        if (!toDelete.isEmpty()) highlightRepository.deleteAll(toDelete);
    }

    private static double seconds(LocalTime t) {
        return t.toNanoOfDay() / 1_000_000_000.0;
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
}
