package io.arknights.dateorfriends.modules.user.online.ws;

import io.arknights.dateorfriends.modules.user.online.service.OnlineRoomService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.jwt.JwtService;
import io.arknights.dateorfriends.tools.jwt.JwtTokenType;
import io.arknights.dateorfriends.tools.security.token.RedisTokenStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    private final OnlineRoomService onlineRoomService;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    private static final int WORLD_W = 1920;
    private static final int WORLD_H = 1080;
    private static final int TICK_HZ = 30;
    private static final Duration TICK_PERIOD = Duration.ofNanos(1_000_000_000L / TICK_HZ);
    private static final long DISCONNECT_GRACE_MS = 30_000L;

    private static final double SPEED = 260.0;
    private static final double SPEED_SHIFT = 800.0;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, RoomMeta> roomMetas = new ConcurrentHashMap<>();

    public OnlineWebSocketHandlerV2(JwtService jwtService, RedisTokenStore tokenStore, OnlineRoomService onlineRoomService) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.onlineRoomService = onlineRoomService;
        var now = System.currentTimeMillis();
        var lobby = new RoomMeta("lobby", "大厅", true, "PUBLIC", 0, null, ConcurrentHashMap.newKeySet(), 0L, now, now);
        roomMetas.put(lobby.roomId, lobby);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        onlineRoomService.loadAllMetas()
                .doOnNext(this::reloadRoomMetasFromDb)
                .subscribe();
    }

    public record RoomCard(
            String roomId,
            String name,
            boolean online,
            String permission,
            int capacity,
            int onlineCount,
            boolean needPassword,
            boolean canEnter,
            String denyReason
    ) {
    }

    public record CreateRoomRequest(
            String roomId,
            String name,
            String permission,
            int capacity,
            String password,
            List<Long> whitelistUserIds,
            Boolean online
    ) {
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
            var password = String.valueOf(msg.getOrDefault("password", ""));
            if (roomId == null || assetKey == null || assetKey.isBlank()) {
                return Mono.empty();
            }
            if (nickname == null || nickname.isBlank()) nickname = "玩家" + ctx.principal.userId();

            var resumeClientId = String.valueOf(msg.getOrDefault("clientId", "")).trim();
            var resumeKey = String.valueOf(msg.getOrDefault("resumeKey", "")).trim();
            joinRoom(ctx, roomId, assetKey, nickname, resumeClientId, resumeKey, password);
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
        if ("leave".equals(type)) {
            var roomId = ctx.roomId;
            if (roomId == null) return Mono.empty();
            var room = rooms.get(roomId);
            if (room == null) return Mono.empty();
            var clientId = ctx.clientId;
            if (clientId == null || clientId.isBlank()) return Mono.empty();

            room.players.remove(clientId);
            room.broadcastAll(toJson(Map.of("type", "player_leave", "clientId", clientId)));

            ctx.roomId = null;
            ctx.slot = null;

            if (room.hostClientId != null && room.hostClientId.equals(clientId)) {
                room.hostClientId = room.pickNextHost();
                room.broadcastAll(toJson(Map.of("type", "host_change", "hostClientId", room.hostClientId, "hostFps", room.hostFps)));
            }

            if (room.players.isEmpty()) {
                room.stopTicker();
                rooms.remove(roomId);
            }
            return Mono.empty();
        }
        if ("change_avatar".equals(type)) {
            var roomId = ctx.roomId;
            if (roomId == null) return Mono.empty();
            var room = rooms.get(roomId);
            if (room == null) return Mono.empty();
            var slot = ctx.slot;
            if (slot == null || slot.state == null) return Mono.empty();
            var rawKey = String.valueOf(msg.getOrDefault("assetKey", "")).trim();
            if (rawKey.isBlank()) return Mono.empty();
            var safeKey = rawKey.replaceAll("[^a-zA-Z0-9_-]", "");
            if (safeKey.isBlank() || safeKey.length() > 128) return Mono.empty();
            slot.state.assetKey = safeKey;
            room.broadcastAll(toJson(Map.of("type", "player_update", "player", stateToMap(slot.state))));
            return Mono.empty();
        }
        if ("emote".equals(type)) {
            var roomId = ctx.roomId;
            if (roomId == null) return Mono.empty();
            var room = rooms.get(roomId);
            if (room == null) return Mono.empty();
            if (ctx.slot == null || ctx.slot.state == null) return Mono.empty();
            var targetId = String.valueOf(msg.getOrDefault("clientId", "")).trim();
            var emote = String.valueOf(msg.getOrDefault("emote", "")).trim();
            if (targetId.isBlank() || emote.isBlank()) return Mono.empty();
            if (emote.length() > 32) emote = emote.substring(0, 32);
            room.broadcastAll(toJson(Map.of("type", "emote", "clientId", targetId, "emote", emote)));
            return Mono.empty();
        }
        return Mono.empty();
    }

    public List<RoomCard> listRooms(JwtPrincipal principal) {
        var keys = new ArrayList<>(roomMetas.keySet());
        keys.sort(String::compareTo);
        var result = new ArrayList<RoomCard>(keys.size());
        var isAdmin = isAdminRole(principal.role());
        for (var id : keys) {
            var meta = roomMetas.get(id);
            if (meta == null) continue;
            var perm = safePermission(meta.permission);
            var needPassword = "PASSWORD".equals(perm);
            var room = rooms.get(meta.roomId);
            var onlineCount = room == null ? 0 : room.connectedCount();
            var canEnter = true;
            String denyReason = null;
            if (!meta.online) {
                canEnter = false;
                denyReason = "离线";
            } else if (meta.capacity > 0 && onlineCount >= meta.capacity) {
                canEnter = false;
                denyReason = "已满";
            } else if ("ADMIN_ONLY".equals(perm) && !isAdmin) {
                canEnter = false;
                denyReason = "仅管理员";
            } else if ("WHITELIST".equals(perm)) {
                if (meta.whitelist == null || (!meta.whitelist.contains(principal.userId()) && meta.creatorUserId != principal.userId())) {
                    canEnter = false;
                    denyReason = "无权限";
                }
            }
            result.add(new RoomCard(meta.roomId, meta.name, meta.online, perm, meta.capacity, onlineCount, needPassword, canEnter, denyReason));
        }
        return result;
    }

    public Mono<RoomCard> createRoom(JwtPrincipal principal, CreateRoomRequest req) {
        return onlineRoomService.createRoom(principal, req)
                .map(res -> {
                    upsertRoomMeta(res.meta());
                    return res.card();
                });
    }

    public Mono<Void> setRoomOnline(JwtPrincipal principal, String roomId, boolean online) {
        return onlineRoomService.setRoomOnline(roomId, online)
                .doOnSuccess(ignored -> {
                    var id = safeRoomId(roomId);
                    var meta = roomMetas.get(id);
                    if (meta != null) {
                        meta.online = online;
                        meta.updatedAt = System.currentTimeMillis();
                    }
                    if (!online) {
                        var room = rooms.remove(id);
                        if (room != null) {
                            room.broadcastAll(toJson(Map.of("type", "room_offline")));
                            room.closeAll();
                            room.stopTicker();
                        }
                    }
                });
    }

    private void deny(ConnCtx ctx, String code, String message) {
        if (ctx == null) return;
        ctx.sink.tryEmitNext(toJson(Map.of("type", "error", "code", code, "message", message)));
    }

    private static boolean isAdminRole(String role) {
        var r = String.valueOf(role == null ? "" : role).trim().toUpperCase();
        return "ADMIN".equals(r) || "SUPER_ADMIN".equals(r);
    }

    private static String safePermission(String raw) {
        var r = String.valueOf(raw == null ? "" : raw).trim().toUpperCase();
        if ("ADMIN_ONLY".equals(r)) return "ADMIN_ONLY";
        if ("PASSWORD".equals(r)) return "PASSWORD";
        if ("WHITELIST".equals(r)) return "WHITELIST";
        return "PUBLIC";
    }

    private static String hashText(String text) {
        try {
            var d = MessageDigest.getInstance("SHA-256");
            var b = d.digest(String.valueOf(text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(b);
        } catch (Exception e) {
            return "";
        }
    }

    private void reloadRoomMetasFromDb(List<OnlineRoomService.RoomMetaData> metas) {
        var next = new ConcurrentHashMap<String, RoomMeta>();
        if (metas != null) {
            for (var m : metas) {
                if (m == null || m.roomId() == null || m.roomId().isBlank()) continue;
                next.put(m.roomId(), toRoomMeta(m));
            }
        }
        if (!next.containsKey("lobby")) {
            var now = System.currentTimeMillis();
            next.put("lobby", new RoomMeta("lobby", "大厅", true, "PUBLIC", 0, null, ConcurrentHashMap.newKeySet(), 0L, now, now));
        }
        roomMetas.clear();
        roomMetas.putAll(next);
    }

    private void upsertRoomMeta(OnlineRoomService.RoomMetaData meta) {
        if (meta == null || meta.roomId() == null || meta.roomId().isBlank()) return;
        roomMetas.put(meta.roomId(), toRoomMeta(meta));
    }

    private RoomMeta toRoomMeta(OnlineRoomService.RoomMetaData meta) {
        var wl = ConcurrentHashMap.<Long>newKeySet();
        if (meta.whitelist() != null) wl.addAll(meta.whitelist());
        var createdAt = meta.createdAt() > 0 ? meta.createdAt() : System.currentTimeMillis();
        var updatedAt = meta.updatedAt() > 0 ? meta.updatedAt() : createdAt;
        return new RoomMeta(
                safeRoomId(meta.roomId()),
                String.valueOf(meta.name() == null ? "" : meta.name()).trim(),
                meta.online(),
                safePermission(meta.permission()),
                Math.max(0, meta.capacity()),
                meta.passwordHash(),
                wl,
                meta.creatorUserId(),
                createdAt,
                updatedAt
        );
    }

    private void joinRoom(ConnCtx ctx, String roomId, String assetKey, String nickname, String resumeClientId, String resumeKey, String password) {
        if (ctx.roomId != null) return;
        var meta = roomMetas.get(roomId);
        if (meta == null) {
            deny(ctx, "ROOM_NOT_FOUND", "房间不存在");
            return;
        }
        if (!meta.online) {
            deny(ctx, "ROOM_OFFLINE", "房间离线");
            return;
        }
        var perm = safePermission(meta.permission);
        if ("ADMIN_ONLY".equals(perm) && !isAdminRole(ctx.principal.role())) {
            deny(ctx, "ROOM_FORBIDDEN", "无权限进入");
            return;
        }
        if ("WHITELIST".equals(perm)) {
            if (meta.whitelist == null || (!meta.whitelist.contains(ctx.principal.userId()) && meta.creatorUserId != ctx.principal.userId())) {
                deny(ctx, "ROOM_FORBIDDEN", "无权限进入");
                return;
            }
        }
        if ("PASSWORD".equals(perm)) {
            var raw = String.valueOf(password == null ? "" : password).trim();
            if (raw.isBlank()) {
                deny(ctx, "ROOM_PASSWORD_REQUIRED", "需要房间密码");
                return;
            }
            var hash = hashText(raw);
            if (meta.passwordHash == null || !meta.passwordHash.equals(hash)) {
                deny(ctx, "ROOM_PASSWORD_INVALID", "房间密码错误");
                return;
            }
        }

        var room = rooms.computeIfAbsent(roomId, k -> new Room(roomId));
        var cap = Math.max(0, meta.capacity);
        if (cap > 0 && room.connectedCount() >= cap) {
            deny(ctx, "ROOM_FULL", "房间已满");
            return;
        }
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
            if (slot.conn != null && slot.conn != ctx) {
                try {
                    slot.conn.sink.tryEmitComplete();
                    slot.conn.session.close(CloseStatus.NORMAL).subscribe();
                } catch (Exception e) {
                }
            }
            slot.state.assetKey = assetKey;
            slot.state.nickname = nickname;
            slot.state.inputX = 0;
            slot.state.inputY = 0;
            slot.state.inputShift = false;
        }

        kickOtherUserPlayers(room, ctx.principal.userId(), slot.state.clientId);

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

    private void kickOtherUserPlayers(Room room, long userId, String keepClientId) {
        var removed = new ArrayList<PlayerSlot>();
        for (var e : room.players.entrySet()) {
            var cid = e.getKey();
            var slot = e.getValue();
            if (slot == null || slot.state == null) continue;
            if (slot.state.userId != userId) continue;
            if (cid != null && cid.equals(keepClientId)) continue;
            removed.add(slot);
        }
        for (var slot : removed) {
            try {
                room.players.remove(slot.state.clientId);
            } catch (Exception e) {
            }
            try {
                room.broadcastAll(toJson(Map.of("type", "player_leave", "clientId", slot.state.clientId)));
            } catch (Exception e) {
            }
            if (slot.conn != null) {
                try {
                    slot.conn.sink.tryEmitComplete();
                    slot.conn.session.close(CloseStatus.NORMAL).subscribe();
                } catch (Exception e) {
                }
                slot.conn = null;
            }
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

    // ---------- 赛马模式外部广播接口 ----------

    public boolean isUserInRoom(String roomId, long userId) {
        if (roomId == null) return false;
        var room = rooms.get(roomId);
        if (room == null) return false;
        for (var slot : room.players.values()) {
            if (slot != null && slot.state != null && slot.state.userId == userId && slot.conn != null) {
                return true;
            }
        }
        return false;
    }

    public void broadcastToRoom(String roomId, String json) {
        if (roomId == null) return;
        var room = rooms.get(roomId);
        if (room != null) room.broadcastAll(json);
    }

    public void sendToUserInRoom(String roomId, long userId, String json) {
        if (roomId == null) return;
        var room = rooms.get(roomId);
        if (room == null) return;
        for (var slot : room.players.values()) {
            if (slot != null && slot.state != null && slot.state.userId == userId && slot.conn != null) {
                slot.conn.sink.tryEmitNext(json);
            }
        }
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

    private static class RoomMeta {
        private final String roomId;
        private volatile String name;
        private volatile boolean online;
        private volatile String permission;
        private volatile int capacity;
        private volatile String passwordHash;
        private final Set<Long> whitelist;
        private final long creatorUserId;
        private final long createdAt;
        private volatile long updatedAt;

        private RoomMeta(
                String roomId,
                String name,
                boolean online,
                String permission,
                int capacity,
                String passwordHash,
                Set<Long> whitelist,
                long creatorUserId,
                long createdAt,
                long updatedAt
        ) {
            this.roomId = roomId;
            this.name = name;
            this.online = online;
            this.permission = permission;
            this.capacity = capacity;
            this.passwordHash = passwordHash;
            this.whitelist = whitelist;
            this.creatorUserId = creatorUserId;
            var now = System.currentTimeMillis();
            this.createdAt = createdAt > 0 ? createdAt : now;
            this.updatedAt = updatedAt > 0 ? updatedAt : this.createdAt;
        }
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

        private int connectedCount() {
            var c = 0;
            for (var slot : players.values()) {
                if (slot != null && slot.conn != null) c += 1;
            }
            return c;
        }

        private void closeAll() {
            for (var slot : players.values()) {
                if (slot == null || slot.conn == null) continue;
                try {
                    slot.conn.sink.tryEmitComplete();
                    slot.conn.session.close(CloseStatus.NORMAL).subscribe();
                } catch (Exception e) {
                }
                slot.conn = null;
            }
            players.clear();
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
