package io.arknights.dateorfriends.modules.user.online.ws;

import io.arknights.dateorfriends.tools.jwt.JwtService;
import io.arknights.dateorfriends.tools.security.token.RedisTokenStore;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import static io.arknights.dateorfriends.modules.user.online.ws.OnlineWsUtils.validateAccessToken;

@Component
public class OnlineCtrlWebSocketHandler implements WebSocketHandler {

    private final JwtService jwtService;
    private final RedisTokenStore tokenStore;

    public OnlineCtrlWebSocketHandler(JwtService jwtService, RedisTokenStore tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        var uri = session.getHandshakeInfo().getUri();
        var token = OnlineWsUtils.queryParam(uri, "token");
        if (token == null || token.isBlank()) {
            return session.close(CloseStatus.POLICY_VIOLATION);
        }
        return validateAccessToken(jwtService, tokenStore, token)
                .flatMap(ignored -> session.receive()
                        .timeout(Duration.ofMinutes(10))
                        .map(WebSocketMessage::getPayloadAsText)
                        .flatMap(text -> {
                            var ts = extractTs(text);
                            var pong = "{\"type\":\"pong\",\"ts\":" + ts + ",\"serverTs\":" + System.currentTimeMillis() + "}";
                            return session.send(Mono.just(session.textMessage(pong))).then();
                        })
                        .onErrorResume(e -> Mono.empty())
                        .then())
                .onErrorResume(e -> session.close(CloseStatus.POLICY_VIOLATION));
    }

    private long extractTs(String text) {
        if (text == null || text.isBlank()) return 0;
        var t = text.trim();
        var idx = t.indexOf("\"ts\"");
        if (idx < 0) return 0;
        var colon = t.indexOf(':', idx);
        if (colon < 0) return 0;
        var end = colon + 1;
        while (end < t.length() && (t.charAt(end) == ' ' || t.charAt(end) == '\t')) end++;
        var sb = new StringBuilder();
        while (end < t.length()) {
            var c = t.charAt(end);
            if (c < '0' || c > '9') break;
            sb.append(c);
            end++;
        }
        if (sb.isEmpty()) return 0;
        try {
            return Long.parseLong(sb.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
