package io.arknights.dateorfriends.modules.user.online.ws;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

@Configuration
public class OnlineWebSocketConfig {

    @Bean
    public HandlerMapping onlineWsMapping(OnlineWebSocketHandlerV2 handler, OnlineCtrlWebSocketHandler ctrlHandler) {
        return new SimpleUrlHandlerMapping(Map.of(
                "/ws/online", handler,
                "/ws/online/ctrl", ctrlHandler
        ), -1);
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
