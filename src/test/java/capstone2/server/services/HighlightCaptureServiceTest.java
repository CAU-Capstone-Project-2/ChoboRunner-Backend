package capstone2.server.services;

import capstone2.server.dto.AiProgressMessage;
import capstone2.server.entities.Highlight;
import capstone2.server.entities.RunSession;
import capstone2.server.repositories.HighlightRepository;
import capstone2.server.repositories.RunSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HighlightCaptureServiceTest {

    private static final String SESSION_ID = "ws-1";
    private static final Long RUN_ID = 10L;

    private HighlightRepository highlightRepo;
    private RunSessionRepository runRepo;
    private HighlightCaptureService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        highlightRepo = mock(HighlightRepository.class);
        runRepo = mock(RunSessionRepository.class);
        when(runRepo.findById(RUN_ID))
                .thenReturn(Optional.of(RunSession.builder().id(RUN_ID).build()));

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

    private void drain() throws Exception {
        ExecutorService worker = (ExecutorService) ReflectionTestUtils.getField(service, "worker");
        worker.submit(() -> {
        }).get(2, TimeUnit.SECONDS);
    }

    private LocalTime toLocalTime(double sec) {
        return LocalTime.ofNanoOfDay(Math.round(sec * 1000) * 1_000_000L);
    }

    @Test
    void briefWarningExtendsEndByMinDuration() throws Exception {
        send(10.0, "trunk_lean", "기울었음");
        sendNoWarnings(20.0);
        drain();

        ArgumentCaptor<Highlight> captor = ArgumentCaptor.forClass(Highlight.class);
        verify(highlightRepo).save(captor.capture());
        Highlight saved = captor.getValue();

        assertThat(saved.getStartTime()).isEqualTo(toLocalTime(9.0));
        assertThat(saved.getEndTime()).isEqualTo(toLocalTime(13.0));
        assertThat(saved.getIssueType()).isEqualTo("trunk_lean");
        assertThat(saved.getMessage()).isEqualTo("기울었음");
    }

    @Test
    void longWarningPadsStartOnly() throws Exception {
        send(5.0, "trunk_lean", "기울었음");
        send(10.0, "trunk_lean", "기울었음");
        send(15.0, "trunk_lean", "기울었음");
        sendNoWarnings(25.0);
        drain();

        ArgumentCaptor<Highlight> captor = ArgumentCaptor.forClass(Highlight.class);
        verify(highlightRepo).save(captor.capture());
        Highlight saved = captor.getValue();

        assertThat(saved.getStartTime()).isEqualTo(toLocalTime(4.0));
        assertThat(saved.getEndTime()).isEqualTo(toLocalTime(18.0));
    }

    @Test
    void startTimeClampedAtZeroWhenPaddingGoesNegative() throws Exception {
        send(0.3, "trunk_lean", "기울었음");
        sendNoWarnings(10.0);
        drain();

        ArgumentCaptor<Highlight> captor = ArgumentCaptor.forClass(Highlight.class);
        verify(highlightRepo).save(captor.capture());

        assertThat(captor.getValue().getStartTime()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void differentMessageOnSameMetricFlushesAndStartsNew() throws Exception {
        send(5.0, "trunk_lean", "메시지A");
        send(6.0, "trunk_lean", "메시지B");
        sendNoWarnings(20.0);
        drain();

        verify(highlightRepo, times(2)).save(any(Highlight.class));
    }

    @Test
    void flushAllPersistsRemainingOpenHighlights() throws Exception {
        send(5.0, "trunk_lean", "기울었음");
        service.flushAll(SESSION_ID);
        drain();

        verify(highlightRepo, times(1)).save(any(Highlight.class));
    }

    @Test
    void ignoresNonProgressMessages() throws Exception {
        AiProgressMessage msg = new AiProgressMessage();
        msg.setType("session_end");
        msg.setElapsedSec(10.0);
        service.onMessage(SESSION_ID, RUN_ID, mapper.writeValueAsString(msg));
        drain();

        verify(highlightRepo, never()).save(any());
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
        sendNoWarnings(20.0);
        drain();

        verify(highlightRepo, never()).save(any());
    }
}
