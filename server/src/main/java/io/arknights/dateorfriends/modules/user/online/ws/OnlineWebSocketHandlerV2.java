package io.arknights.dateorfriends.modules.user.online.ws;

import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.jwt.JwtService;
import io.arknights.dateorfriends.tools.jwt.JwtTokenType;
import io.arknights.dateorfriends.tools.security.token.RedisTokenStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
public class OnlineWebSocketHandlerV2 implements WebSocketHandler {

    private final JwtService jwtService;
    private final RedisTokenStore tokenStore;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    private static final int WORLD_W = 1920;
    private static final int WORLD_H = 1080;
    private static final int TICK_HZ = 30;
    private static final Duration TICK_PERIOD = Duration.ofNanos(1_000_000_000L / TICK_HZ);
    private static final long DISCONNECT_GRACE_MS = 30_000L;

    private static final double SPEED = 260.0;
    private static final double SPEED_SHIFT = 800.0;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public OnlineWebSocketHandlerV2(JwtService jwtService, RedisTokenStore tokenStore) {
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
                    var ctx = new ConnCtx(session, sink, principal);
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

    private Mono<Void> onMessage(ConnCtx ctx, URI uri, String text) {
        Map<String, Object> msg;
        try {
            msg = jsonParser.parseMap(text);
        } catch (Exception e) {
            return Mono.empty();
        }
        var type = String.valueOf(msg.getOrDefault("type", ""));
        if ("join".equals(type)) {
            var roomRaw = msg.get("roomId") != null ? String.valueOf(msg.get("roomId")) : queryParam(uri, "room");
            var roomId = safeRoomId(roomRaw);
            var assetKey = String.valueOf(msg.getOrDefault("assetKey", ""));
            var nickname = String.valueOf(msg.getOrDefault("nickname", ""));
            if (roomId == null || assetKey == null || assetKey.isBlank()) {
                return Mono.empty();
            }
            if (nickname == null || nickname.isBlank()) nickname = "玩家" + ctx.principal.userId();

            var resumeClientId = String.valueOf(msg.getOrDefault("clientId", "")).trim();
            var resumeKey = String.valueOf(msg.getOrDefault("resumeKey", "")).trim();
            joinRoom(ctx, roomId, assetKey, nickname, resumeClientId, resumeKey);
            return Mono.empty();
        }
        if ("input".equals(type)) {
            var roomId = ctx.roomId;
            if (roomId == null) return Mono.empty();
            var room = rooms.get(roomId);
            if (room == null) return Mono.empty();
            var slot = ctx.slot;
            if (slot == null) return Mono.empty();
            var s = slot.state;
            if (s == null) return Mono.empty();
            var seq = toLong(msg.get("seq"), 0L);
            var dx = toDouble(msg.get("dx"), 0.0);
            var dy = toDouble(msg.get("dy"), 0.0);
            var shift = toBool(msg.get("shift"), false);
            var dir = toInt(msg.get("dir"), s.dir);
            if (seq <= s.lastInputSeq) return Mono.empty();
            s.lastInputSeq = seq;
            s.inputX = clamp(dx, -1.0, 1.0);
            s.inputY = clamp(dy, -1.0, 1.0);
            s.inputShift = shift;
            s.dir = dir == -1 ? -1 : 1;
            return Mono.empty();
        }
        if ("host_fps".equals(type)) {
            var roomId = ctx.roomId;
            if (roomId == null) return Mono.empty();
            var room = rooms.get(roomId);
            if (room == null) return Mono.empty();
            if (ctx.slot == null || ctx.slot.state == null) return Mono.empty();
            if (!String.valueOf(ctx.slot.state.clientId).equals(room.hostClientId)) return Mono.empty();
            var v = toInt(msg.get("fps"), 0);
            if (v <= 0) return Mono.empty();
            var safe = Math.min(120, Math.max(30, v));
            room.hostFps = safe;
            room.broadcastAll(toJson(Map.of("type", "host_fps", "fps", room.hostFps)));
            return Mono.empty();
        }
        return Mono.empty();
    }

    private void joinRoom(ConnCtx ctx, String roomId, String assetKey, String nickname, String resumeClientId, String resumeKey) {
        if (ctx.roomId != null) return;
        var room = rooms.computeIfAbsent(roomId, k -> new Room(roomId));
        room.startTicker();

        PlayerSlot slot = null;
        var resumed = false;
        if (resumeClientId != null && !resumeClientId.isBlank() && resumeKey != null && !resumeKey.isBlank()) {
            var existed = room.players.get(resumeClientId);
            if (existed != null && resumeKey.equals(existed.resumeKey)) {
                slot = existed;
                resumed = true;
            }
        }
        if (slot == null) {
            var clientId = "u" + ctx.principal.userId() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            var key = UUID.randomUUID().toString().replace("-", "");
            var s = new PlayerState(clientId, ctx.principal.userId(), nickname, assetKey);
            s.x = WORLD_W / 2.0;
            s.y = WORLD_H / 2.0;
            slot = new PlayerSlot(s, key);
            room.players.put(clientId, slot);
        } else {
            slot.state.assetKey = assetKey;
            slot.state.nickname = nickname;
            slot.state.inputX = 0;
            slot.state.inputY = 0;
            slot.state.inputShift = false;
        }

        slot.conn = ctx;
        slot.disconnectedAt = 0L;
        ctx.roomId = roomId;
        ctx.slot = slot;
        ctx.clientId = slot.state.clientId;

        if (room.hostClientId == null || room.hostClientId.isBlank() || room.getConnected(room.hostClientId) == null) {
            room.hostClientId = slot.state.clientId;
        }

        var players = room.players.values().stream()
                .map(x -> x.state)
                .filter(x -> x != null)
                .map(this::stateToMap)
                .toList();

        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("type", "welcome");
        payload.put("clientId", slot.state.clientId);
        payload.put("resumeKey", slot.resumeKey);
        payload.put("roomId", roomId);
        payload.put("worldW", WORLD_W);
        payload.put("worldH", WORLD_H);
        payload.put("tickHz", TICK_HZ);
        payload.put("serverTs", System.currentTimeMillis());
        payload.put("hostClientId", room.hostClientId);
        payload.put("hostFps", room.hostFps);
        payload.put("players", players);
        ctx.sink.tryEmitNext(toJson(payload));

        if (!resumed) {
            room.broadcastExcept(slot.state.clientId, toJson(Map.of("type", "player_join", "player", stateToMap(slot.state))));
        }
    }

    private Map<String, Object> stateToMap(PlayerState s) {
        return Map.of(
                "clientId", s.clientId,
                "userId", s.userId,
                "nickname", s.nickname,
                "assetKey", s.assetKey,
                "x", s.x,
                "y", s.y,
                "moving", s.moving,
                "dir", s.dir,
                "seq", s.lastProcessedSeq
        );
    }

    private void cleanup(ConnCtx ctx) {
        ctx.sink.tryEmitComplete();
        var roomId = ctx.roomId;
        if (roomId == null) return;
        var room = rooms.get(roomId);
        if (room == null) return;

        var slot = ctx.slot;
        if (slot != null && slot.conn == ctx) {
            slot.conn = null;
            slot.disconnectedAt = System.currentTimeMillis();
            var s = slot.state;
            if (s != null) {
                s.inputX = 0;
                s.inputY = 0;
                s.inputShift = false;
                s.moving = false;
            }
        }
        ctx.roomId = null;
        ctx.slot = null;

        if (room.hostClientId != null && room.getConnected(room.hostClientId) == null) {
            room.hostClientId = room.pickNextHost();
            room.broadcastAll(toJson(Map.of("type", "host_change", "hostClientId", room.hostClientId, "hostFps", room.hostFps)));
        }
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

    private long toLong(Object v, long fallback) {
        if (v == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(v));
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

    private double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private class Room {
        private final String roomId;
        private final Map<String, PlayerSlot> players = new ConcurrentHashMap<>();
        private volatile String hostClientId;
        private volatile int hostFps = 60;
        private volatile Disposable ticker;
        
        private Room(String roomId) {
            this.roomId = roomId;
        }

        private void broadcastExcept(String clientId, String json) {
            for (var entry : players.entrySet()) {
                if (entry.getKey().equals(clientId)) continue;
                var conn = entry.getValue() == null ? null : entry.getValue().conn;
                if (conn == null) continue;
                conn.sink.tryEmitNext(json);
            }
        }

        private void broadcastAll(String json) {
            for (var slot : players.values()) {
                if (slot == null || slot.conn == null) continue;
                slot.conn.sink.tryEmitNext(json);
            }
        }

        private PlayerSlot getConnected(String clientId) {
            if (clientId == null || clientId.isBlank()) return null;
            var slot = players.get(clientId);
            if (slot == null || slot.conn == null) return null;
            return slot;
        }

        private String pickNextHost() {
            for (var e : players.entrySet()) {
                var slot = e.getValue();
                if (slot != null && slot.conn != null) {
                    return e.getKey();
                }
            }
            return null;
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
            if (players.isEmpty()) return;

            var now = System.currentTimeMillis();
            var removed = new ArrayList<String>();
            for (var e : players.entrySet()) {
                var slot = e.getValue();
                if (slot == null) continue;
                if (slot.conn != null) continue;
                if (slot.disconnectedAt > 0 && now - slot.disconnectedAt >= DISCONNECT_GRACE_MS) {
                    removed.add(e.getKey());
                }
            }
            for (var id : removed) {
                players.remove(id);
                broadcastAll(toJson(Map.of("type", "player_leave", "clientId", id)));
            }
            if (players.isEmpty()) {
                stopTicker();
                rooms.remove(roomId);
                return;
            }

            var dt = 1.0 / TICK_HZ;
            for (var slot : players.values()) {
                if (slot == null || slot.state == null) continue;
                stepPlayer(slot.state, dt);
            }

            var list = players.values().stream()
                    .map(x -> x == null ? null : x.state)
                    .filter(x -> x != null)
                    .map(OnlineWebSocketHandlerV2.this::stateToMap)
                    .toList();

            broadcastAll(toJson(Map.of(
                    "type", "snapshot",
                    "serverTs", now,
                    "hostClientId", hostClientId,
                    "hostFps", hostFps,
                    "players", list
            )));
        }

        private void stepPlayer(PlayerState s, double dt) {
            var ix = s.inputX;
            var iy = s.inputY;
            var len = Math.hypot(ix, iy);
            var nx = len > 1e-6 ? ix / len : 0.0;
            var ny = len > 1e-6 ? iy / len : 0.0;
            var speed = s.inputShift ? SPEED_SHIFT : SPEED;
            s.x = clamp(s.x + nx * speed * dt, 0.0, WORLD_W);
            s.y = clamp(s.y + ny * speed * dt, 0.0, WORLD_H);
            s.moving = len > 1e-6;
            s.lastProcessedSeq = s.lastInputSeq;
        }
    }

    private static class PlayerSlot {
        private final PlayerState state;
        private final String resumeKey;
        private volatile ConnCtx conn;
        private volatile long disconnectedAt;

        private PlayerSlot(PlayerState state, String resumeKey) {
            this.state = state;
            this.resumeKey = resumeKey;
        }
    }

    private static class PlayerState {
        private final String clientId;
        private final long userId;
        private volatile String nickname;
        private volatile String assetKey;
        private double x;
        private double y;
        private boolean moving;
        private int dir = 1;
        private double inputX;
        private double inputY;
        private boolean inputShift;
        private long lastInputSeq;
        private long lastProcessedSeq;

        private PlayerState(String clientId, long userId, String nickname, String assetKey) {
            this.clientId = clientId;
            this.userId = userId;
            this.nickname = nickname;
            this.assetKey = assetKey;
        }
    }

    private static class ConnCtx {
        private final WebSocketSession session;
        private final Sinks.Many<String> sink;
        private final JwtPrincipal principal;
        private volatile String roomId;
        private volatile String clientId;
        private volatile PlayerSlot slot;

        private ConnCtx(WebSocketSession session, Sinks.Many<String> sink, JwtPrincipal principal) {
            this.session = session;
            this.sink = sink;
            this.principal = principal;
        }
    }
}
