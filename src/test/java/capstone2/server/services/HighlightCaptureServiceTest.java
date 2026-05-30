package capstone2.server.services;

import capstone2.server.dto.AiProgressMessage;
import capstone2.server.entities.Highlight;
import capstone2.server.entities.RunSession;
import capstone2.server.repositories.HighlightRepository;
import capstone2.server.repositories.RunSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HighlightCaptureServiceTest {

    private static final String SESSION_ID = "ws-1";
    private static final Long RUN_ID = 10L;

    private HighlightRepository highlightRepo;
    private RunSessionRepository runRepo;
    private HighlightCaptureService service;
    private final ObjectMapper mapper = new ObjectMapper();

    /** save/delete 를 반영하는 인메모리 저장소 — 후처리 병합 결과를 검증하기 위함 */
    private final List<Highlight> store = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong();

    @BeforeEach
    void setUp() {
        store.clear();
        idSeq.set(0);
        highlightRepo = mock(HighlightRepository.class);
        runRepo = mock(RunSessionRepository.class);
        when(runRepo.findById(RUN_ID))
                .thenReturn(Optional.of(RunSession.builder().id(RUN_ID).build()));

        when(highlightRepo.save(any(Highlight.class))).thenAnswer(inv -> {
            Highlight h = inv.getArgument(0);
            if (h.getId() == null) {
                h.setId(idSeq.incrementAndGet());
                store.add(h);
            }
            return h;
        });
        when(highlightRepo.findByRunSessionId(anyLong())).thenAnswer(inv -> new ArrayList<>(store));
        doAnswer(inv -> {
            Iterable<Highlight> toDelete = inv.getArgument(0);
            toDelete.forEach(store::remove);
            return null;
        }).when(highlightRepo).deleteAll(any());

        service = new HighlightCaptureService(highlightRepo, runRepo);
        ReflectionTestUtils.setField(service, "gapThresholdSec", 6.0);
        ReflectionTestUtils.setField(service, "minHighlightSec", 3.0);
        ReflectionTestUtils.setField(service, "startPaddingSec", 1.0);
    }

    private String payloadOf(double elapsedSec, List<String[]> warnings) throws Exception {
        AiProgressMessage msg = new AiProgressMessage();
        msg.setType("analysis_progress");
        msg.setElapsedSec(elapsedSec);
        msg.setFeedbackMessages(warnings.stream().map(arr -> {
            AiProgressMessage.FeedbackMessage fb = new AiProgressMessage.FeedbackMessage();
            fb.setCategory("posture_warning");
            fb.setMetric(arr[0]);
            fb.setDisplayText(arr[1]);
            return fb;
        }).toList());
        return mapper.writeValueAsString(msg);
    }

    private void send(double elapsedSec, String metric, String text) throws Exception {
        service.onMessage(SESSION_ID, RUN_ID,
                payloadOf(elapsedSec, List.<String[]>of(new String[]{metric, text})));
    }

    private void sendNoWarnings(double elapsedSec) throws Exception {
        service.onMessage(SESSION_ID, RUN_ID, payloadOf(elapsedSec, List.of()));
    }

    /** flushAll 까지 포함한 모든 작업이 worker 에서 끝나도록 대기 */
    private void flushAndDrain() throws Exception {
        service.flushAll(SESSION_ID);
        ExecutorService worker = (ExecutorService) ReflectionTestUtils.getField(service, "worker");
        worker.submit(() -> {
        }).get(2, TimeUnit.SECONDS);
    }

    private LocalTime toLocalTime(double sec) {
        return LocalTime.ofNanoOfDay(Math.round(sec * 1000) * 1_000_000L);
    }

    @Test
    void singleWarningStoredWithStartPaddingAndMinDuration() throws Exception {
        send(10.0, "trunk_lean", "기울었음");
        flushAndDrain();

        assertThat(store).hasSize(1);
        Highlight saved = store.get(0);
        assertThat(saved.getStartTime()).isEqualTo(toLocalTime(9.0));
        assertThat(saved.getEndTime()).isEqualTo(toLocalTime(13.0));
        assertThat(saved.getIssueType()).isEqualTo("trunk_lean");
        assertThat(saved.getMessage()).isEqualTo("기울었음");
    }

    @Test
    void contiguousSameWarningsMergeIntoOne() throws Exception {
        send(5.0, "trunk_lean", "기울었음");
        send(10.0, "trunk_lean", "기울었음");
        send(15.0, "trunk_lean", "기울었음");
        flushAndDrain();

        assertThat(store).hasSize(1);
        assertThat(store.get(0).getStartTime()).isEqualTo(toLocalTime(4.0));
        assertThat(store.get(0).getEndTime()).isEqualTo(toLocalTime(18.0));
    }

    @Test
    void startTimeClampedAtZeroWhenPaddingGoesNegative() throws Exception {
        send(0.3, "trunk_lean", "기울었음");
        flushAndDrain();

        assertThat(store).hasSize(1);
        assertThat(store.get(0).getStartTime()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void differentMessageOnSameMetricStaysSeparate() throws Exception {
        send(5.0, "trunk_lean", "메시지A");
        send(6.0, "trunk_lean", "메시지B");
        flushAndDrain();

        assertThat(store).hasSize(2);
    }

    @Test
    void farApartSameWarningsStaySeparate() throws Exception {
        // 두 번째 경고가 gap(6s) 보다 멀리 떨어져 있으면 병합하지 않는다.
        send(5.0, "trunk_lean", "기울었음");   // [4, 8]
        send(20.0, "trunk_lean", "기울었음");  // [19, 23], 8 + 6 = 14 < 19
        flushAndDrain();

        assertThat(store).hasSize(2);
    }

    @Test
    void duplicateSameMessageWithinGapMergesAndExtendsPrevEnd() throws Exception {
        // 보고된 케이스: knee_flexion 동일 메시지가 다른 메시지(B) 뒤에 인접 두 번 → 마지막을 앞으로 병합.
        String a = "무릎 굴곡이 일반 범위(15~25°)보다 작게 관찰됩니다.";
        String b = "착지 시 무릎 굴곡이 일반 범위보다 큽니다.";
        send(2.0, "knee_flexion", a);   // [1, 5]
        send(8.0, "knee_flexion", b);   // [7, 11]
        send(10.0, "knee_flexion", a);  // [9, 13]
        send(15.0, "knee_flexion", a);  // [14, 18] → [9,13] 으로 병합 (13 + 6 = 19 > 14)
        flushAndDrain();

        assertThat(store)
                .extracting(Highlight::getIssueType, Highlight::getMessage,
                        Highlight::getStartTime, Highlight::getEndTime)
                .containsExactlyInAnyOrder(
                        tuple("knee_flexion", a, toLocalTime(1.0), toLocalTime(5.0)),
                        tuple("knee_flexion", b, toLocalTime(7.0), toLocalTime(11.0)),
                        tuple("knee_flexion", a, toLocalTime(9.0), toLocalTime(18.0)));
    }

    @Test
    void ignoresNonProgressMessages() throws Exception {
        AiProgressMessage msg = new AiProgressMessage();
        msg.setType("session_end");
        msg.setElapsedSec(10.0);
        service.onMessage(SESSION_ID, RUN_ID, mapper.writeValueAsString(msg));
        flushAndDrain();

        assertThat(store).isEmpty();
    }

    @Test
    void ignoresFeedbackMessagesOtherThanPostureWarning() throws Exception {
        AiProgressMessage msg = new AiProgressMessage();
        msg.setType("analysis_progress");
        msg.setElapsedSec(10.0);
        AiProgressMessage.FeedbackMessage fb = new AiProgressMessage.FeedbackMessage();
        fb.setCategory("positive_feedback");
        fb.setMetric("stability");
        fb.setDisplayText("자세가 안정적입니다");
        msg.setFeedbackMessages(List.of(fb));
        service.onMessage(SESSION_ID, RUN_ID, mapper.writeValueAsString(msg));
        flushAndDrain();

        assertThat(store).isEmpty();
    }
}
