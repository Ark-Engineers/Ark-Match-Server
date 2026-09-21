package io.arknights.dateorfriends.modules.user.online.ws;

import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.jwt.JwtService;
import io.arknights.dateorfriends.tools.jwt.JwtTokenType;
import io.arknights.dateorfriends.tools.security.token.RedisTokenStore;
import io.arknights.dateorfriends.tools.web.IpUtils;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public final class OnlineWsUtils {

    private OnlineWsUtils() {
    }

    public static Mono<JwtPrincipal> validateAccessToken(JwtService jwtService, RedisTokenStore tokenStore, String token) {
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

    public static String safeRoomId(String raw) {
        var s = String.valueOf(raw == null ? "" : raw).trim();
        if (s.isBlank()) s = "lobby";
        s = s.replaceAll("[^a-zA-Z0-9_-]", "");
        if (s.isBlank()) return "lobby";
        if (s.length() > 32) s = s.substring(0, 32);
        return s;
    }

    public static String queryParam(URI uri, String key) {
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

    private static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    public static String toJson(Object obj) {
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

    public static String escape(String s) {
        return IpUtils.maskInText(s).replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static double toDouble(Object v, double fallback) {
        if (v == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static int toInt(Object v, int fallback) {
        if (v == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static long toLong(Object v, long fallback) {
        if (v == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static boolean toBool(Object v, boolean fallback) {
        if (v == null) return fallback;
        var s = String.valueOf(v);
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        return fallback;
    }

    public static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
