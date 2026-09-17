package io.arknights.dateorfriends.modules.user.online.service;

import io.arknights.dateorfriends.modules.user.online.mapper.OnlineRoomDO;
import io.arknights.dateorfriends.modules.user.online.mapper.OnlineRoomMapper;
import io.arknights.dateorfriends.modules.user.online.mapper.OnlineRoomWhitelistDO;
import io.arknights.dateorfriends.modules.user.online.mapper.OnlineRoomWhitelistMapper;
import io.arknights.dateorfriends.modules.user.online.ws.OnlineWebSocketHandlerV2;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class OnlineRoomService {

    public record RoomMetaData(
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
    }

    public record CreateResult(OnlineWebSocketHandlerV2.RoomCard card, RoomMetaData meta) {
    }

    private final OnlineRoomMapper roomMapper;
    private final OnlineRoomWhitelistMapper whitelistMapper;

    public OnlineRoomService(OnlineRoomMapper roomMapper, OnlineRoomWhitelistMapper whitelistMapper) {
        this.roomMapper = roomMapper;
        this.whitelistMapper = whitelistMapper;
    }

    public Mono<List<RoomMetaData>> loadAllMetas() {
        return Mono.fromCallable(() -> {
                    ensureLobby();
                    var rooms = roomMapper.selectAll();
                    var wls = whitelistMapper.selectAll();
                    Map<String, Set<Long>> wlMap = new ConcurrentHashMap<>();
                    for (var x : wls) {
                        if (x == null) continue;
                        var id = normalizeRoomId(x.getRoomId());
                        if (id == null) continue;
                        var uid = x.getUserId();
                        if (uid == null || uid <= 0) continue;
                        wlMap.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(uid);
                    }
                    List<RoomMetaData> out = new ArrayList<>(rooms.size());
                    for (var r : rooms) {
                        if (r == null) continue;
                        var id = normalizeRoomId(r.getRoomId());
                        if (id == null) continue;
                        var perm = normalizePermission(r.getPermission());
                        var name = normalizeName(r.getName(), id);
                        var cap = Math.max(0, r.getCapacity() == null ? 0 : r.getCapacity());
                        var creator = r.getCreatorUserId() == null ? 0L : r.getCreatorUserId();
                        var createdAt = toMillis(r.getCreatedAt());
                        var updatedAt = toMillis(r.getUpdatedAt());
                        var online = r.getOnline() != null && r.getOnline() == 1;
                        var wl = wlMap.getOrDefault(id, ConcurrentHashMap.newKeySet());
                        out.add(new RoomMetaData(
                                id,
                                name,
                                online,
                                perm,
                                cap,
                                r.getPasswordHash(),
                                wl,
                                creator,
                                createdAt,
                                updatedAt
                        ));
                    }
                    return out;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<CreateResult> createRoom(JwtPrincipal principal, OnlineWebSocketHandlerV2.CreateRoomRequest req) {
        return Mono.fromCallable(() -> {
                    var rawId = req == null ? null : req.roomId();
                    var id = normalizeRoomId(rawId);
                    if (id == null || id.isBlank()) throw new IllegalArgumentException("roomId");
                    if (roomMapper.selectByRoomId(id) != null) throw new IllegalStateException("room exists");

                    var name = normalizeName(req == null ? null : req.name(), id);
                    var perm = normalizePermission(req == null ? null : req.permission());
                    var capacity = Math.max(0, req == null ? 0 : req.capacity());
                    var online = req == null || req.online() == null || Boolean.TRUE.equals(req.online());

                    String passwordHash = null;
                    if ("PASSWORD".equals(perm)) {
                        var pw = String.valueOf(req == null ? "" : req.password()).trim();
                        if (pw.isBlank()) throw new IllegalArgumentException("password");
                        passwordHash = hashText(pw);
                    }

                    Set<Long> wl = ConcurrentHashMap.newKeySet();
                    if (req != null && req.whitelistUserIds() != null) {
                        for (var x : req.whitelistUserIds()) {
                            if (x == null) continue;
                            if (x > 0) wl.add(x);
                        }
                    }

                    var now = LocalDateTime.now();
                    var r = new OnlineRoomDO();
                    r.setRoomId(id);
                    r.setName(name);
                    r.setOnline(online ? 1 : 0);
                    r.setPermission(perm);
                    r.setCapacity(capacity);
                    r.setPasswordHash(passwordHash);
                    r.setCreatorUserId(principal == null ? 0L : principal.userId());
                    r.setCreatedAt(now);
                    r.setUpdatedAt(now);
                    r.setDeleted(0);
                    roomMapper.insert(r);

                    whitelistMapper.deleteByRoomId(id);
                    if (!wl.isEmpty()) {
                        var items = new ArrayList<OnlineRoomWhitelistDO>(wl.size());
                        for (var uid : wl) {
                            var it = new OnlineRoomWhitelistDO();
                            it.setRoomId(id);
                            it.setUserId(uid);
                            it.setCreatedAt(now);
                            items.add(it);
                        }
                        whitelistMapper.insertBatch(items);
                    }

                    var meta = new RoomMetaData(
                            id,
                            name,
                            online,
                            perm,
                            capacity,
                            passwordHash,
                            wl,
                            principal == null ? 0L : principal.userId(),
                            System.currentTimeMillis(),
                            System.currentTimeMillis()
                    );

                    var card = new OnlineWebSocketHandlerV2.RoomCard(
                            id,
                            name,
                            online,
                            perm,
                            capacity,
                            0,
                            "PASSWORD".equals(perm),
                            true,
                            null
                    );
                    return new CreateResult(card, meta);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> setRoomOnline(String roomId, boolean online) {
        return Mono.fromCallable(() -> {
                    var id = normalizeRoomId(roomId);
                    var existed = roomMapper.selectByRoomId(id);
                    if (existed == null || (existed.getDeleted() != null && existed.getDeleted() == 1)) {
                        throw new IllegalArgumentException("roomId");
                    }
                    var updatedAt = LocalDateTime.now();
                    var rows = roomMapper.updateOnline(id, online ? 1 : 0, updatedAt);
                    if (rows <= 0) throw new IllegalArgumentException("roomId");
                    return (Void) null;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void ensureLobby() {
        var lobbyId = "lobby";
        if (roomMapper.selectByRoomId(lobbyId) != null) return;
        var now = LocalDateTime.now();
        var lobby = new OnlineRoomDO();
        lobby.setRoomId(lobbyId);
        lobby.setName("大厅");
        lobby.setOnline(1);
        lobby.setPermission("PUBLIC");
        lobby.setCapacity(0);
        lobby.setPasswordHash(null);
        lobby.setCreatorUserId(0L);
        lobby.setCreatedAt(now);
        lobby.setUpdatedAt(now);
        lobby.setDeleted(0);
        roomMapper.insertIgnore(lobby);
    }

    private static String normalizeRoomId(String raw) {
        var s = String.valueOf(raw == null ? "" : raw).trim();
        if (s.isBlank()) s = "lobby";
        s = s.replaceAll("[^a-zA-Z0-9_-]", "");
        if (s.isBlank()) return "lobby";
        if (s.length() > 32) s = s.substring(0, 32);
        return s;
    }

    private static String normalizePermission(String raw) {
        var r = String.valueOf(raw == null ? "" : raw).trim().toUpperCase();
        if ("ADMIN_ONLY".equals(r)) return "ADMIN_ONLY";
        if ("PASSWORD".equals(r)) return "PASSWORD";
        if ("WHITELIST".equals(r)) return "WHITELIST";
        return "PUBLIC";
    }

    private static String normalizeName(String raw, String fallback) {
        var s = String.valueOf(raw == null ? "" : raw).trim();
        if (s.isBlank()) s = String.valueOf(fallback == null ? "" : fallback).trim();
        if (s.isBlank()) s = "room";
        if (s.length() > 32) s = s.substring(0, 32);
        return s;
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

    private static long toMillis(LocalDateTime t) {
        if (t == null) return 0L;
        try {
            return t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }
}
