package io.arknights.dateorfriends.tools.web;

import io.netty.util.NetUtil;
import java.util.regex.Pattern;
import org.springframework.web.server.ServerWebExchange;

public final class IpUtils {
    private static final Pattern IPV4_LITERAL = Pattern.compile("(?<![\\p{Alnum}_.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![\\p{Alnum}_]|\\.[0-9])");
    private static final Pattern IPV6_LITERAL = Pattern.compile("(?<![\\p{Alnum}_.])(?:[0-9a-fA-F]{0,4}:){2,8}[0-9a-fA-F.]{0,45}(?:%[\\p{Alnum}_.-]+)?(?![\\p{Alnum}_:.])");

    private IpUtils() {
    }

    public static String mask(String ip) {
        return ip == null || ip.isBlank() ? ip : "***";
    }

    public static String maskInText(String text) {
        if (text == null || (text.indexOf('.') < 0 && text.indexOf(':') < 0)) return text;
        var ipv6Masked = IPV6_LITERAL.matcher(text).replaceAll(match -> {
            var candidate = match.group();
            var start = candidate.startsWith(":") && !candidate.startsWith("::") ? 1 : 0;
            var end = candidate.length();
            while (end > start && candidate.charAt(end - 1) == '.') end--;
            return NetUtil.isValidIpV6Address(candidate.substring(start, end))
                    ? candidate.substring(0, start) + "***" + candidate.substring(end) : candidate;
        });
        return IPV4_LITERAL.matcher(ipv6Masked).replaceAll(match ->
                NetUtil.isValidIpV4Address(match.group()) ? "***" : match.group());
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
