package capstone2.server.config;

import capstone2.server.websocket.VideoRelayHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VideoRelayHandler videoRelayHandler;
    private final int wsMaxMessageSize;

    public WebSocketConfig(
            VideoRelayHandler videoRelayHandler,
            @Value("${app.websocket.max-message-size-bytes:10485760}") int wsMaxMessageSize
    ) {
        this.videoRelayHandler = videoRelayHandler;
        this.wsMaxMessageSize = wsMaxMessageSize;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 일반 WebSocket 클라이언트용 엔드포인트
        registry.addHandler(videoRelayHandler, "/ws/chobo-runner")
            .setAllowedOriginPatterns("*");

        // SockJS 클라이언트가 필요하면 이 경로를 사용
        registry.addHandler(videoRelayHandler, "/ws/chobo-runner-sockjs")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(wsMaxMessageSize);
        container.setMaxBinaryMessageBufferSize(wsMaxMessageSize);
        return container;
    }
}