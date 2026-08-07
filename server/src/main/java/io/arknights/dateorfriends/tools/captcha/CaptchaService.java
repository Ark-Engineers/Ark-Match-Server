package io.arknights.dateorfriends.tools.captcha;

import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";

    private final ReactiveStringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    private final Duration ttl;
    private final String secret;

    public CaptchaService(
            ReactiveStringRedisTemplate redis,
            @Value("${app.verify.captcha-ttl-seconds:300}") long captchaTtlSeconds,
            @Value("${app.verify.secret:dev-verify-secret-change-me}") String secret
    ) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(Math.max(30, captchaTtlSeconds));
        this.secret = secret == null ? "" : secret;
    }

    public record CaptchaChallenge(String captchaId, String svg) {
    }

    public record CaptchaDebug(boolean exists, long ttlSeconds) {
    }

    private static volatile String DBG_URL;
    private static volatile String DBG_SESSION;

    private void dbg(String hypothesisId, String location, String msg, String dataJson) {
        // #region debug-point H?:captcha
        try {
            var url = DBG_URL;
            var session = DBG_SESSION;
            if (url == null || url.isBlank() || session == null || session.isBlank()) {
                var p = Path.of(".dbg", "captcha-not-working.env");
                if (Files.exists(p)) {
                    var raw = Files.readString(p, StandardCharsets.UTF_8);
                    var lines = raw.split("\\R");
                    for (var line : lines) {
                        if (line.startsWith("DEBUG_SERVER_URL=")) url = line.substring("DEBUG_SERVER_URL=".length()).trim();
                        if (line.startsWith("DEBUG_SESSION_ID=")) session = line.substring("DEBUG_SESSION_ID=".length()).trim();
                    }
                }
                if (url == null || url.isBlank()) url = System.getenv("DEBUG_SERVER_URL");
                if (session == null || session.isBlank()) session = System.getenv("DEBUG_SESSION_ID");
                if (session == null || session.isBlank()) session = "captcha-not-working";
                DBG_URL = url;
                DBG_SESSION = session;
            }
            if (url == null || url.isBlank()) return;
            var body = "{\"sessionId\":\"" + session + "\",\"runId\":\"pre\",\"hypothesisId\":\"" + hypothesisId + "\",\"location\":\"" + location + "\",\"msg\":\"[DEBUG] " + msg.replace("\"", "'") + "\",\"data\":" + (dataJson == null ? "{}" : dataJson) + ",\"ts\":" + System.currentTimeMillis() + "}";
            HttpClient.newHttpClient().sendAsync(
                    HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.discarding()
            ).exceptionally(e -> null);
        } catch (Exception ignored) {
        }
        // #endregion
    }

    public Mono<CaptchaChallenge> create() {
        var text = randomText(4);
        var captchaId = randomSalt();
        var salt = randomSalt();
        var hash = sha256Hex(secret + ":" + salt + ":" + text.toUpperCase());
        var key = KEY_PREFIX + captchaId;
        var stored = salt + "|" + hash;
        dbg("H3", "CaptchaService:create", "captcha create start", "{\"captchaId\":\"" + captchaId + "\",\"ttlSeconds\":" + ttl.toSeconds() + "}");
        return redis.opsForValue()
                .set(key, stored, ttl)
                .flatMap(ok -> {
                    dbg("H3", "CaptchaService:create", "captcha create redis set", "{\"captchaId\":\"" + captchaId + "\",\"ok\":" + (Boolean.TRUE.equals(ok) ? "true" : "false") + "}");
                    if (Boolean.TRUE.equals(ok)) {
                        return Mono.just(new CaptchaChallenge(captchaId, toSvg(text)));
                    }
                    return Mono.error(new BusinessException(ErrorCode.INTERNAL_ERROR, "图形验证码生成失败"));
                });
    }

    public Mono<CaptchaDebug> debug(String captchaId) {
        var id = captchaId == null ? "" : captchaId.trim();
        if (id.isBlank()) {
            return Mono.just(new CaptchaDebug(false, 0));
        }
        var key = KEY_PREFIX + id;
        return redis.hasKey(key)
                .flatMap(exists -> {
                    if (!Boolean.TRUE.equals(exists)) {
                        return Mono.just(new CaptchaDebug(false, 0));
                    }
                    return redis.getExpire(key)
                            .defaultIfEmpty(Duration.ZERO)
                            .map(ttl -> new CaptchaDebug(true, ttl == null ? 0 : ttl.toSeconds()));
                });
    }

    public Mono<Void> verify(String captchaId, String captchaText) {
        return verifyInternal(captchaId, captchaText, true);
    }

    public Mono<Void> verifyKeep(String captchaId, String captchaText) {
        return verifyInternal(captchaId, captchaText, false);
    }

    public Mono<Void> consume(String captchaId) {
        var id = captchaId == null ? "" : captchaId.trim();
        if (id.isBlank()) return Mono.empty();
        return redis.delete(KEY_PREFIX + id).then();
    }

    private Mono<Void> verifyInternal(String captchaId, String captchaText, boolean consume) {
        var id = captchaId == null ? "" : captchaId.trim();
        var rawText = captchaText == null ? "" : captchaText.trim();
        final String text = rawText.length() > 4 ? rawText.substring(0, 4) : rawText;
        dbg("H4", "CaptchaService:verify", "captcha verify start", "{\"consume\":" + (consume ? "true" : "false") + ",\"captchaId\":\"" + id + "\",\"textLen\":" + text.length() + "}");
        if (id.isBlank() || text.isBlank()) {
            return Mono.error(new BusinessException(ErrorCode.CAPTCHA_INVALID));
        }
        if (text.length() != 4) {
            return Mono.error(new BusinessException(ErrorCode.CAPTCHA_INVALID));
        }
        var key = KEY_PREFIX + id;
        return getStoredWithRetry(key)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.CAPTCHA_EXPIRED)))
                .flatMap(stored -> {
                    dbg("H3", "CaptchaService:verify", "captcha verify stored fetched", "{\"captchaId\":\"" + id + "\",\"storedBlank\":" + (stored == null || stored.isBlank() ? "true" : "false") + "}");
                    if (stored == null || stored.isBlank()) {
                        return Mono.error(new BusinessException(ErrorCode.CAPTCHA_EXPIRED));
                    }
                    var parts = stored.split("\\|", 2);
                    if (parts.length != 2) {
                        return redis.delete(key).then(Mono.error(new BusinessException(ErrorCode.CAPTCHA_EXPIRED)));
                    }
                    var salt = parts[0];
                    var hash = parts[1];
                    var expected = sha256Hex(secret + ":" + salt + ":" + text.toUpperCase());
                    if (!constantTimeEquals(hash, expected)) {
                        dbg("H4", "CaptchaService:verify", "captcha verify mismatch", "{\"captchaId\":\"" + id + "\"}");
                        return Mono.error(new BusinessException(ErrorCode.CAPTCHA_INVALID));
                    }
                    dbg("H4", "CaptchaService:verify", "captcha verify ok", "{\"captchaId\":\"" + id + "\",\"consume\":" + (consume ? "true" : "false") + "}");
                    if (consume) return redis.delete(key).then();
                    return Mono.empty();
                });
    }

    private Mono<String> getStoredWithRetry(String key) {
        return redis.opsForValue()
                .get(key)
                .switchIfEmpty(Mono.delay(Duration.ofMillis(120)).then(redis.opsForValue().get(key)));
    }

    private String randomText(int len) {
        final String chars = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
        var sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String randomSalt() {
        var bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String toSvg(String text) {
        var t = escapeXml(text);
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="132" height="44" viewBox="0 0 132 44">
                  <defs>
                    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0" stop-color="#f0f9ff"/>
                      <stop offset="1" stop-color="#ffffff"/>
                    </linearGradient>
                  </defs>
                  <rect x="0" y="0" width="132" height="44" rx="10" fill="url(#g)" stroke="rgba(14,165,233,0.25)"/>
                  <path d="M8 12 C 30 2, 60 22, 124 10" stroke="rgba(14,165,233,0.25)" stroke-width="2" fill="none"/>
                  <path d="M8 32 C 34 44, 70 22, 124 34" stroke="rgba(14,165,233,0.20)" stroke-width="2" fill="none"/>
                  <g fill="rgba(15,23,42,0.90)" font-family="ui-monospace,Menlo,Consolas,monospace" font-weight="800">
                    <text x="18" y="30" font-size="22">%s</text>
                    <text x="46" y="30" font-size="22">%s</text>
                    <text x="74" y="30" font-size="22">%s</text>
                    <text x="102" y="30" font-size="22">%s</text>
                  </g>
                </svg>
                """.formatted(
                t.substring(0, 1),
                t.substring(1, 2),
                t.substring(2, 3),
                t.substring(3, 4)
        );
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String sha256Hex(String raw) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int res = 0;
        for (int i = 0; i < a.length(); i++) {
            res |= a.charAt(i) ^ b.charAt(i);
        }
        return res == 0;
    }
}
