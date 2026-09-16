package io.arknights.dateorfriends.modules.user.online.ws;

import io.arknights.dateorfriends.tools.jwt.JwtService;
import io.arknights.dateorfriends.tools.jwt.JwtTokenType;
import io.arknights.dateorfriends.tools.security.token.RedisTokenStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

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
        var token = queryParam(uri, "token");
        if (token == null || token.isBlank()) {
            return session.close(CloseStatus.POLICY_VIOLATION);
        }
        return validate(token)
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

    private Mono<Integer> validate(String token) {
        try {
            var principal = jwtService.parseAndValidate(token, JwtTokenType.ACCESS);
            return tokenStore.isBlacklisted(principal.jti())
                    .flatMap(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) return Mono.error(new IllegalStateException("token revoked"));
                        return tokenStore.getTokenVersion(principal.userId()).flatMap(ver -> {
                            if (ver != principal.tokenVersion()) return Mono.error(new IllegalStateException("token revoked"));
                            return Mono.just(0);
                        });
                    });
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("invalid token"));
        }
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

    private String queryParam(URI uri, String key) {
        var q = uri.getRawQuery();
        if (q == null || q.isBlank()) return null;
        for (var part : q.split("&")) {
            if (part.isBlank()) continue;
            var kv = part.split("=", 2);
            var k = decode(kv[0]);
            if (!key.equals(k)) continue;
            return kv.length > 1 ? decode(kv[1]) : "";
        }
        return null;
    }

    private String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
