package io.arknights.dateorfriends.modules.user.online.ws;

import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.jwt.JwtService;
import io.arknights.dateorfriends.tools.jwt.JwtTokenType;
import io.arknights.dateorfriends.tools.security.token.RedisTokenStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Component
public class OnlineWebSocketHandler implements WebSocketHandler {

    private final JwtService jwtService;
    private final RedisTokenStore tokenStore;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    private static final int WORLD_W = 1920;
    private static final int WORLD_H = 1080;
    private static final int TICK_HZ = 90;
    private static final Duration TICK_PERIOD = Duration.ofNanos(1_000_000_000L / TICK_HZ);

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public OnlineWebSocketHandler(JwtService jwtService, RedisTokenStore tokenStore) {
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
        return validateToken(token)
                .flatMap(principal -> {
                    var sink = Sinks.many().unicast().<String>onBackpressureBuffer();
                    var clientId = "u" + principal.userId() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                    var ctx = new SessionCtx(session, sink, principal, clientId);
                    var send = session.send(sink.asFlux().map(session::textMessage));

                    var receive = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .flatMap(text -> onMessage(ctx, uri, text))
                            .onErrorResume(e -> Mono.empty())
                            .then()
                            .doFinally(sig -> cleanup(ctx));

                    return Mono.when(send, receive);
                })
                .onErrorResume(e -> session.close(CloseStatus.POLICY_VIOLATION));
    }

    private Mono<Void> onMessage(SessionCtx ctx, URI uri, String text) {
        Map<String, Object> msg;
        try {
            msg = jsonParser.parseMap(text);
        } catch (Exception e) {
            return Mono.empty();
        }
        var type = String.valueOf(msg.getOrDefault("type", ""));
        if ("ping".equals(type)) {
            return reply(ctx, Map.of("type", "pong", "ts", msg.get("ts")));
        }
        if ("join".equals(type)) {
            var roomRaw = msg.get("roomId") != null ? String.valueOf(msg.get("roomId")) : queryParam(uri, "room");
            var roomId = safeRoomId(roomRaw);
            var assetKey = String.valueOf(msg.getOrDefault("assetKey", ""));
            var nickname = String.valueOf(msg.getOrDefault("nickname", ""));
            if (roomId == null || assetKey == null || assetKey.isBlank()) {
                return Mono.empty();
            }
            if (nickname == null || nickname.isBlank()) nickname = "玩家" + ctx.principal.userId();
            joinRoom(ctx, roomId, assetKey, nickname);
            return Mono.empty();
        }
        if ("host_fps".equals(type)) {
            var roomId = ctx.roomId;
            if (roomId == null) return Mono.empty();
            var room = rooms.get(roomId);
            if (room == null) return Mono.empty();
            if (!ctx.clientId.equals(room.hostClientId)) return Mono.empty();
            var v = toInt(msg.get("fps"), 0);
            if (v <= 0) return Mono.empty();
            var safe = Math.min(120, Math.max(30, v));
            room.hostFps = safe;
            room.broadcastAll(toJson(Map.of("type", "host_fps", "fps", room.hostFps)));
            return Mono.empty();
        }
        if ("state".equals(type)) {
            var roomId = ctx.roomId;
            if (roomId == null) return Mono.empty();
            var room = rooms.get(roomId);
            if (room == null) return Mono.empty();
            var s = ctx.state;
            if (s == null) return Mono.empty();
            s.x = toDouble(msg.get("x"), s.x);
            s.y = toDouble(msg.get("y"), s.y);
            s.moving = toBool(msg.get("moving"), s.moving);
            s.dir = toInt(msg.get("dir"), s.dir);
            var anim = String.valueOf(msg.getOrDefault("anim", ""));
            if (anim != null && !anim.isBlank()) s.anim = anim;
            return Mono.empty();
        }
        return Mono.empty();
    }

    private void joinRoom(SessionCtx ctx, String roomId, String assetKey, String nickname) {
        if (ctx.roomId != null) return;
        var room = rooms.computeIfAbsent(roomId, k -> new Room());
        if (room.hostClientId == null || room.hostClientId.isBlank()) {
            room.hostClientId = ctx.clientId;
        }
        var s = new PlayerState(ctx.clientId, ctx.principal.userId(), nickname, assetKey);
        s.x = WORLD_W / 2.0;
        s.y = WORLD_H / 2.0;
        ctx.roomId = roomId;
        ctx.state = s;
        room.sessions.put(ctx.clientId, ctx);
        room.startTicker();

        var players = room.sessions.values().stream()
                .map(x -> x.state)
                .filter(x -> x != null)
                .map(x -> Map.of(
                        "clientId", x.clientId,
                        "userId", x.userId,
                        "nickname", x.nickname,
                        "assetKey", x.assetKey,
                        "x", x.x,
                        "y", x.y,
                        "moving", x.moving,
                        "dir", x.dir,
                        "anim", x.anim
                ))
                .toList();

        ctx.sink.tryEmitNext(toJson(Map.of(
                "type", "welcome",
                "clientId", ctx.clientId,
                "roomId", roomId,
                "worldW", WORLD_W,
                "worldH", WORLD_H,
                "hostClientId", room.hostClientId,
                "hostFps", room.hostFps,
                "players", players
        )));

        room.broadcastExcept(ctx.clientId, toJson(Map.of(
                "type", "player_join",
                "player", Map.of(
                        "clientId", s.clientId,
                        "userId", s.userId,
                        "nickname", s.nickname,
                        "assetKey", s.assetKey,
                        "x", s.x,
                        "y", s.y,
                        "moving", s.moving,
                        "dir", s.dir,
                        "anim", s.anim
                )
        )));
    }

    private void cleanup(SessionCtx ctx) {
        ctx.sink.tryEmitComplete();
        var roomId = ctx.roomId;
        if (roomId == null) return;
        var room = rooms.get(roomId);
        if (room == null) return;
        room.sessions.remove(ctx.clientId);
        room.broadcastExcept(ctx.clientId, toJson(Map.of("type", "player_leave", "clientId", ctx.clientId)));
        if (ctx.clientId.equals(room.hostClientId)) {
            var nextHost = room.sessions.keySet().stream().findFirst().orElse(null);
            room.hostClientId = nextHost;
            if (nextHost != null) {
                room.broadcastAll(toJson(Map.of(
                        "type", "host_change",
                        "hostClientId", room.hostClientId,
                        "hostFps", room.hostFps
                )));
            }
        }
        if (room.sessions.isEmpty()) {
            room.stopTicker();
            rooms.remove(roomId);
        }
    }

    private Mono<Void> reply(SessionCtx ctx, Map<String, Object> payload) {
        ctx.sink.tryEmitNext(toJson(payload));
        return Mono.empty();
    }

    private Mono<JwtPrincipal> validateToken(String token) {
        JwtPrincipal principal;
        try {
            principal = jwtService.parseAndValidate(token, JwtTokenType.ACCESS);
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("invalid token"));
        }
        return tokenStore.isBlacklisted(principal.jti())
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) return Mono.error(new IllegalStateException("token revoked"));
                    return tokenStore.getTokenVersion(principal.userId()).flatMap(ver -> {
                        if (ver != principal.tokenVersion()) return Mono.error(new IllegalStateException("token revoked"));
                        return Mono.just(principal);
                    });
                });
    }

    private String safeRoomId(String raw) {
        var s = String.valueOf(raw == null ? "" : raw).trim();
        if (s.isBlank()) s = "lobby";
        s = s.replaceAll("[^a-zA-Z0-9_-]", "");
        if (s.isBlank()) return "lobby";
        if (s.length() > 32) s = s.substring(0, 32);
        return s;
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

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String s) return "\"" + escape(s) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
        if (obj instanceof Map<?, ?> m) {
            var sb = new StringBuilder();
            sb.append("{");
            var first = true;
            for (var e : m.entrySet()) {
                if (e.getKey() == null) continue;
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escape(String.valueOf(e.getKey()))).append("\":");
                sb.append(toJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List<?> list) {
            var sb = new StringBuilder();
            sb.append("[");
            for (var i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escape(String.valueOf(obj)) + "\"";
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private double toDouble(Object v, double fallback) {
        if (v == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    private int toInt(Object v, int fallback) {
        if (v == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean toBool(Object v, boolean fallback) {
        if (v == null) return fallback;
        var s = String.valueOf(v);
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        return fallback;
    }

    private class Room {
        private final Map<String, SessionCtx> sessions = new ConcurrentHashMap<>();
        private volatile String hostClientId;
        private volatile int hostFps = 60;
        private volatile Disposable ticker;
        private final Map<String, PlayerSnapshot> lastSent = new ConcurrentHashMap<>();

        private void broadcastExcept(String clientId, String json) {
            for (var entry : sessions.entrySet()) {
                if (entry.getKey().equals(clientId)) continue;
                var ctx = entry.getValue();
                if (ctx == null) continue;
                ctx.sink.tryEmitNext(json);
            }
        }

        private void broadcastAll(String json) {
            for (var ctx : sessions.values()) {
                if (ctx == null) continue;
                ctx.sink.tryEmitNext(json);
            }
        }

        private void startTicker() {
            if (ticker != null && !ticker.isDisposed()) return;
            ticker = Flux.interval(TICK_PERIOD)
                    .publishOn(Schedulers.parallel())
                    .subscribe(ignored -> tickOnce());
        }

        private void stopTicker() {
            if (ticker != null) {
                ticker.dispose();
                ticker = null;
            }
        }

        private void tickOnce() {
            if (sessions.isEmpty()) return;
            var changed = sessions.values().stream()
                    .map(x -> x.state)
                    .filter(x -> x != null)
                    .filter(x -> shouldSend(x))
                    .map(x -> Map.of(
                            "clientId", x.clientId,
                            "x", x.x,
                            "y", x.y,
                            "moving", x.moving,
                            "dir", x.dir,
                            "anim", x.anim
                    ))
                    .toList();
            if (!changed.isEmpty()) {
                broadcastAll(toJson(Map.of("type", "tick", "players", changed)));
            }
        }

        private boolean shouldSend(PlayerState s) {
            var prev = lastSent.get(s.clientId);
            var snap = new PlayerSnapshot(s.x, s.y, s.moving, s.dir, s.anim);
            if (prev == null) {
                lastSent.put(s.clientId, snap);
                return true;
            }
            if (Math.abs(prev.x - snap.x) >= 0.5
                    || Math.abs(prev.y - snap.y) >= 0.5
                    || prev.moving != snap.moving
                    || prev.dir != snap.dir
                    || !String.valueOf(prev.anim).equals(String.valueOf(snap.anim))) {
                lastSent.put(s.clientId, snap);
                return true;
            }
            return false;
        }
    }

    private record PlayerSnapshot(double x, double y, boolean moving, int dir, String anim) {
    }

    private static class PlayerState {
        private final String clientId;
        private final long userId;
        private final String nickname;
        private final String assetKey;
        private double x = 0;
        private double y = 0;
        private boolean moving = false;
        private int dir = 1;
        private String anim = "";

        private PlayerState(String clientId, long userId, String nickname, String assetKey) {
            this.clientId = clientId;
            this.userId = userId;
            this.nickname = nickname;
            this.assetKey = assetKey;
        }
    }

    private static class SessionCtx {
        private final WebSocketSession session;
        private final Sinks.Many<String> sink;
        private final JwtPrincipal principal;
        private final String clientId;
        private volatile String roomId;
        private volatile PlayerState state;

        private SessionCtx(WebSocketSession session, Sinks.Many<String> sink, JwtPrincipal principal, String clientId) {
            this.session = session;
            this.sink = sink;
            this.principal = principal;
            this.clientId = clientId;
        }
    }
}
