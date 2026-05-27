package io.arknights.dateorfriends.tools.web;

import org.springframework.web.server.ServerWebExchange;

public final class IpUtils {
    private IpUtils() {
    }

    public static String resolveClientIp(ServerWebExchange exchange) {
        var xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        var ipFromXff = firstIpFromXff(xff);
        if (ipFromXff != null) {
            return ipFromXff;
        }

        var xri = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        var ipFromXri = normalizeIp(xri);
        if (ipFromXri != null) {
            return ipFromXri;
        }

        var forwarded = exchange.getRequest().getHeaders().getFirst("Forwarded");
        var ipFromForwarded = firstIpFromForwarded(forwarded);
        if (ipFromForwarded != null) {
            return ipFromForwarded;
        }

        var addr = exchange.getRequest().getRemoteAddress();
        if (addr == null || addr.getAddress() == null) {
            return "unknown";
        }
        return addr.getAddress().getHostAddress();
    }

    private static String firstIpFromXff(String xff) {
        if (xff == null || xff.isBlank()) return null;
        var parts = xff.split(",");
        String firstValid = null;
        for (var raw : parts) {
            var ip = normalizeIp(raw);
            if (ip == null) continue;
            if (firstValid == null) firstValid = ip;
            if (isPublicIp(ip)) return ip;
        }
        return firstValid;
    }

    private static String firstIpFromForwarded(String forwarded) {
        if (forwarded == null || forwarded.isBlank()) return null;
        var parts = forwarded.split(";");
        String firstValid = null;
        for (var part : parts) {
            var trimmed = part.trim();
            if (!trimmed.regionMatches(true, 0, "for=", 0, "for=".length())) continue;
            var ip = normalizeIp(trimmed.substring("for=".length()));
            if (ip == null) continue;
            if (firstValid == null) firstValid = ip;
            if (isPublicIp(ip)) return ip;
        }
        return firstValid;
    }

    private static String normalizeIp(String raw) {
        if (raw == null) return null;
        var ip = raw.trim();
        if (ip.isBlank() || ip.equalsIgnoreCase("unknown")) return null;
        if (ip.startsWith("\"") && ip.endsWith("\"") && ip.length() >= 2) {
            ip = ip.substring(1, ip.length() - 1).trim();
        }
        if (ip.startsWith("[") && ip.contains("]")) {
            ip = ip.substring(1, ip.indexOf(']')).trim();
            return ip.isBlank() ? null : ip;
        }
        var colonIdx = ip.lastIndexOf(':');
        if (colonIdx > 0 && ip.indexOf(':') == colonIdx) {
            var host = ip.substring(0, colonIdx).trim();
            var port = ip.substring(colonIdx + 1).trim();
            if (!host.isBlank() && port.matches("\\d{1,5}")) return host;
        }
        return ip.isBlank() ? null : ip;
    }

    public static boolean isPublicIp(String ip) {
        if (ip == null) return false;
        var s = ip.trim();
        if (s.isBlank() || "unknown".equalsIgnoreCase(s)) return false;

        if (s.contains(":")) {
            var lower = s.toLowerCase();
            if ("::1".equals(lower)) return false;
            if (lower.startsWith("fe80:")) return false;
            if (lower.startsWith("fc") || lower.startsWith("fd")) return false;
            return true;
        }

        var parts = s.split("\\.");
        if (parts.length != 4) return false;
        int a;
        int b;
        try {
            a = Integer.parseInt(parts[0]);
            b = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return false;
        }
        if (a == 10) return false;
        if (a == 127) return false;
        if (a == 0) return false;
        if (a == 169 && b == 254) return false;
        if (a == 192 && b == 168) return false;
        if (a == 172 && b >= 16 && b <= 31) return false;
        return true;
    }
}
