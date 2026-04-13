package capstone2.server.websocket;

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

    // FastAPI 서버 주소 (환경에 맞게 수정)
    private final URI pythonAiUri = URI.create("ws://127.0.0.1:8000/ws/inference");
    private final WebSocketClient pythonClient = new ReactorNettyWebSocketClient();
    private final Map<String, CompletableFuture<WebSocketSession>> pythonSessionsByAndroidId = new ConcurrentHashMap<>();
    private final int wsMaxMessageSize;

    public VideoRelayHandler(@Value("${app.websocket.max-message-size-bytes:10485760}") int wsMaxMessageSize) {
        this.wsMaxMessageSize = wsMaxMessageSize;
    }

    @Override
    public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession androidSession) {
        androidSession.setBinaryMessageSizeLimit(wsMaxMessageSize);
        androidSession.setTextMessageSizeLimit(wsMaxMessageSize);
        System.out.println("[ws] Android connected: " + androidSession.getId());
        CompletableFuture<WebSocketSession> future = new CompletableFuture<>();
        pythonSessionsByAndroidId.put(androidSession.getId(), future);

        pythonClient.execute(pythonAiUri, pythonSession -> {
            future.complete(pythonSession);
            System.out.println("[ws] Python connected for Android: " + androidSession.getId());

            return pythonSession.receive()
                .doOnNext(message -> {
                    try {
                        if (!androidSession.isOpen()) {
                            return;
                        }
                        if (message.getType() == WebSocketMessage.Type.TEXT) {
                            System.out.println("[ws] Python text received: " + androidSession.getId());
                            androidSession.sendMessage(new TextMessage(message.getPayloadAsText()));
                        } else if (message.getType() == WebSocketMessage.Type.BINARY) {
                            System.out.println("[ws] Python binary received: " + androidSession.getId());
                            byte[] payload = new byte[message.getPayload().readableByteCount()];
                            message.getPayload().read(payload);
                            androidSession.sendMessage(new BinaryMessage(payload));
                        }
                    } catch (Exception e) {
                        System.err.println("Android 전달 에러: " + e.getMessage());
                        e.printStackTrace();
                        safeClose(androidSession, CloseStatus.SERVER_ERROR);
                    }
                })
                .then();
        }).doOnError(error -> {
            future.completeExceptionally(error);
            System.err.println("AI 연결 에러: " + error.getMessage());
            error.printStackTrace();
            safeClose(androidSession, CloseStatus.SERVER_ERROR);
        }).subscribe();
    }

    @Override
    protected void handleBinaryMessage(org.springframework.web.socket.WebSocketSession androidSession, BinaryMessage message) {
        System.out.println("[ws] Android binary received: " + androidSession.getId() + ", bytes=" + message.getPayloadLength());
        forwardBinaryToPython(androidSession, message);
    }

    @Override
    protected void handleTextMessage(org.springframework.web.socket.WebSocketSession androidSession, TextMessage message) {
        System.out.println("[ws] Android text received: " + androidSession.getId());
        forwardTextToPython(androidSession, message);
    }

    @Override
    public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession androidSession, CloseStatus status) {
        CompletableFuture<WebSocketSession> pythonSessionFuture = pythonSessionsByAndroidId.remove(androidSession.getId());
        closeFutureSession(pythonSessionFuture, status);
    }

    @Override
    public void handleTransportError(org.springframework.web.socket.WebSocketSession androidSession, Throwable exception) {
        CompletableFuture<WebSocketSession> pythonSessionFuture = pythonSessionsByAndroidId.remove(androidSession.getId());
        closeFutureSession(pythonSessionFuture, CloseStatus.SERVER_ERROR);
        safeClose(androidSession, CloseStatus.SERVER_ERROR);
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
            System.err.println("Python 세션 준비 실패: " + e.getMessage());
            e.printStackTrace();
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
            pythonSession.send(
                Mono.just(pythonSession.binaryMessage(factory -> factory.wrap(payload)))
            ).subscribe();
            System.out.println("[ws] Forwarded to Python: " + androidSession.getId());
        } catch (Exception e) {
            System.err.println("Python 전달 에러: " + e.getMessage());
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
            System.err.println("Python 세션 준비 실패: " + e.getMessage());
            e.printStackTrace();
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
            System.out.println("[ws] Forwarded to Python: " + androidSession.getId());
        } catch (Exception e) {
            System.err.println("Python 전달 에러: " + e.getMessage());
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