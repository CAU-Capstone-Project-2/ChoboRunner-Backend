package capstone2.server.websocket;

import capstone2.server.services.HighlightCaptureService;
import capstone2.server.services.posture.PostureResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Value;

import java.nio.ByteBuffer;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class VideoRelayHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VideoRelayHandler.class);

    // Android binary frame format: [8B BE long ts_ms (monotonic, SystemClock.elapsedRealtimeNanos()/1e6)][JPEG bytes]
    private static final int TIMESTAMP_HEADER_BYTES = 8;

    private final URI pythonAiUri;
    private final WebSocketClient pythonClient = new ReactorNettyWebSocketClient();
    private final Map<String, CompletableFuture<WebSocketSession>> pythonSessionsByAndroidId = new ConcurrentHashMap<>();
    private final Map<String, Long> runIdByAndroidId = new ConcurrentHashMap<>();
    private final int wsMaxMessageSize;
    private final HighlightCaptureService highlightCaptureService;
    private final PostureResultService postureResultService;


    public VideoRelayHandler(
            @Value("${ai-server.ws-url}") String wsUrl,
            @Value("${app.websocket.max-message-size-bytes:10485760}") int wsMaxMessageSize,
            HighlightCaptureService highlightCaptureService,
            PostureResultService postureResultService
    ) {
        this.pythonAiUri = URI.create(wsUrl);
        this.wsMaxMessageSize = wsMaxMessageSize;
        this.highlightCaptureService = highlightCaptureService;
        this.postureResultService = postureResultService;
    }

    @Override
    public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession androidSession) {
        androidSession.setBinaryMessageSizeLimit(wsMaxMessageSize);
        androidSession.setTextMessageSizeLimit(wsMaxMessageSize);
        log.info("[ws] Android connected: {}", androidSession.getId());

        Long runId = extractRunId(androidSession.getUri());
        if (runId != null) {
            runIdByAndroidId.put(androidSession.getId(), runId);
            log.info("[ws] runId={} bound to {}", runId, androidSession.getId());
        } else {
            log.info("[ws] no runId query param; highlight capture disabled for {}", androidSession.getId());
        }

        CompletableFuture<WebSocketSession> future = new CompletableFuture<>();
        pythonSessionsByAndroidId.put(androidSession.getId(), future);

        pythonClient.execute(pythonAiUri, pythonSession -> {
            future.complete(pythonSession);
            log.info("[ws] Python connected for Android: {}", androidSession.getId());

            return pythonSession.receive()
                .doOnNext(message -> {
                    try {
                        if (!androidSession.isOpen()) {
                            return;
                        }
                        if (message.getType() == WebSocketMessage.Type.TEXT) {
                            log.info("[ws] Python text received: {}", androidSession.getId());
                            String text = message.getPayloadAsText();
                            Long boundRunId = runIdByAndroidId.get(androidSession.getId());
                            if (boundRunId != null) {
                                highlightCaptureService.onMessage(androidSession.getId(), boundRunId, text);
                                postureResultService.onMessage(boundRunId, text);
                            }
                            androidSession.sendMessage(new TextMessage(text));
                        } else if (message.getType() == WebSocketMessage.Type.BINARY) {
                            log.info("[ws] Python binary received: {}", androidSession.getId());
                            byte[] payload = new byte[message.getPayload().readableByteCount()];
                            message.getPayload().read(payload);
                            androidSession.sendMessage(new BinaryMessage(payload));
                        }
                    } catch (Exception e) {
                        log.error("Android 전달 에러", e);
                        safeClose(androidSession, CloseStatus.SERVER_ERROR);
                    }
                })
                .then();
        }).doOnError(error -> {
            future.completeExceptionally(error);
            log.error("AI 연결 에러", error);
            safeClose(androidSession, CloseStatus.SERVER_ERROR);
        }).subscribe();
    }

    @Override
    protected void handleBinaryMessage(org.springframework.web.socket.WebSocketSession androidSession, BinaryMessage message) {
        log.info("[ws] Android binary received: {}, bytes={}", androidSession.getId(), message.getPayloadLength());
        forwardBinaryToPython(androidSession, message);
    }

    @Override
    protected void handleTextMessage(org.springframework.web.socket.WebSocketSession androidSession, TextMessage message) {
        log.info("[ws] Android text received: {}", androidSession.getId());
        forwardTextToPython(androidSession, message);
    }

    @Override
    public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession androidSession, CloseStatus status) {
        CompletableFuture<WebSocketSession> pythonSessionFuture = pythonSessionsByAndroidId.remove(androidSession.getId());
        closeFutureSession(pythonSessionFuture, status);
        runIdByAndroidId.remove(androidSession.getId());
        highlightCaptureService.flushAll(androidSession.getId());
    }

    @Override
    public void handleTransportError(org.springframework.web.socket.WebSocketSession androidSession, Throwable exception) {
        CompletableFuture<WebSocketSession> pythonSessionFuture = pythonSessionsByAndroidId.remove(androidSession.getId());
        closeFutureSession(pythonSessionFuture, CloseStatus.SERVER_ERROR);
        runIdByAndroidId.remove(androidSession.getId());
        highlightCaptureService.flushAll(androidSession.getId());
        safeClose(androidSession, CloseStatus.SERVER_ERROR);
    }

    private Long extractRunId(URI uri) {
        if (uri == null) {
            return null;
        }
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String key = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            if ("runId".equals(key)) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void forwardBinaryToPython(org.springframework.web.socket.WebSocketSession androidSession, BinaryMessage message) {
        CompletableFuture<WebSocketSession> future = pythonSessionsByAndroidId.get(androidSession.getId());
        if (future == null) {
            safeClose(androidSession, CloseStatus.SERVER_ERROR);
            return;
        }

        WebSocketSession pythonSession;
        try {
            pythonSession = future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Python 세션 준비 실패", e);
            safeClose(androidSession, CloseStatus.SERVER_ERROR);
            return;
        }

        if (pythonSession == null || !pythonSession.isOpen()) {
            safeClose(androidSession, CloseStatus.SERVER_ERROR);
            return;
        }
        try {
            ByteBuffer buffer = message.getPayload().asReadOnlyBuffer();
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            if (payload.length < TIMESTAMP_HEADER_BYTES) {
                log.warn("[ws] Binary payload too small (need 8B ts header): {}, bytes={}",
                        androidSession.getId(), payload.length);
                return;
            }

            long captureTsMs = ByteBuffer.wrap(payload, 0, TIMESTAMP_HEADER_BYTES).getLong();

            pythonSession.send(
                Mono.just(pythonSession.binaryMessage(factory -> factory.wrap(payload)))
            ).subscribe();
            log.info("[ws] Forwarded to Python: {}, ts_ms={}, imgBytes={}",
                    androidSession.getId(), captureTsMs, payload.length - TIMESTAMP_HEADER_BYTES);
        } catch (Exception e) {
            log.error("Python 전달 에러", e);
            safeClose(androidSession, CloseStatus.SERVER_ERROR);
            safeCloseReactive(pythonSession, CloseStatus.SERVER_ERROR);
        }
    }

    private void forwardTextToPython(org.springframework.web.socket.WebSocketSession androidSession, TextMessage message) {
        CompletableFuture<WebSocketSession> future = pythonSessionsByAndroidId.get(androidSession.getId());
        if (future == null) {
            safeClose(androidSession, CloseStatus.SERVER_ERROR);
            return;
        }

        WebSocketSession pythonSession;
        try {
            pythonSession = future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Python 세션 준비 실패", e);
            safeClose(androidSession, CloseStatus.SERVER_ERROR);
            return;
        }

        if (pythonSession == null || !pythonSession.isOpen()) {
            safeClose(androidSession, CloseStatus.SERVER_ERROR);
            return;
        }
        try {
            pythonSession.send(
                Mono.just(pythonSession.textMessage(message.getPayload()))
            ).subscribe();
            log.info("[ws] Forwarded to Python: {}", androidSession.getId());
        } catch (Exception e) {
            log.error("Python 전달 에러", e);
            safeClose(androidSession, CloseStatus.SERVER_ERROR);
            safeCloseReactive(pythonSession, CloseStatus.SERVER_ERROR);
        }
    }

    private void closeFutureSession(CompletableFuture<WebSocketSession> sessionFuture, CloseStatus status) {
        if (sessionFuture == null) {
            return;
        }
        try {
            WebSocketSession session = sessionFuture.get(1, TimeUnit.SECONDS);
            safeCloseReactive(session, status);
        } catch (Exception ignored) {
            // 세션이 아직 준비되지 않았거나 이미 종료된 경우 무시
        }
    }

    private void safeClose(org.springframework.web.socket.WebSocketSession session, CloseStatus status) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (Exception ignored) {
            // close 중 예외는 무시
        }
    }

    private void safeCloseReactive(WebSocketSession session, CloseStatus status) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(new org.springframework.web.reactive.socket.CloseStatus(status.getCode(), status.getReason())).subscribe();
        } catch (Exception ignored) {
            // close 중 예외는 무시
        }
    }
}