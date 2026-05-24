package io.arknights.dateorfriends.tools.verify;

import io.arknights.dateorfriends.tools.mail.MailService;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class EmailCodeService {

    private static final String KEY_CODE_PREFIX = "email:code:";
    private static final String KEY_COOLDOWN_PREFIX = "email:cooldown:";
    private static final String KEY_RL_IP_PREFIX = "rl:ip:";
    private static final String KEY_RL_EMAIL_PREFIX = "rl:email:";

    private final ReactiveStringRedisTemplate redis;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();

    private final Duration codeTtl;
    private final Duration cooldownTtl;
    private final int maxAttempts;
    private final int ipPerMinute;
    private final int emailPerHour;
    private final String secret;

    public EmailCodeService(
            ReactiveStringRedisTemplate redis,
            MailService mailService,
            @Value("${app.verify.code-ttl-seconds:300}") long codeTtlSeconds,
            @Value("${app.verify.cooldown-seconds:60}") long cooldownSeconds,
            @Value("${app.verify.max-attempts:5}") int maxAttempts,
            @Value("${app.verify.rate.ip-per-minute:5}") int ipPerMinute,
            @Value("${app.verify.rate.email-per-hour:10}") int emailPerHour,
            @Value("${app.verify.secret:dev-verify-secret-change-me}") String secret
    ) {
        this.redis = redis;
        this.mailService = mailService;
        this.codeTtl = Duration.ofSeconds(Math.max(60, codeTtlSeconds));
        this.cooldownTtl = Duration.ofSeconds(Math.max(10, cooldownSeconds));
        this.maxAttempts = Math.max(3, maxAttempts);
        this.ipPerMinute = Math.max(1, ipPerMinute);
        this.emailPerHour = Math.max(1, emailPerHour);
        this.secret = secret == null ? "" : secret;
    }

    public record CooldownInfo(long remainingSeconds) {
    }

    public Mono<Void> sendRegisterCode(String email, String ip) {
        return sendCode("register", email, ip, "注册验证码", "你的注册验证码为：%s\n有效期：5分钟\n如非本人操作请忽略。");
    }

    public Mono<Void> sendLoginCode(String email, String ip) {
        return sendCode("login", email, ip, "登录验证码", "你的登录验证码为：%s\n有效期：5分钟\n如非本人操作请忽略。");
    }

    public Mono<Void> verifyRegisterCode(String email, String code, String ip) {
        return verifyCode("register", email, code, ip);
    }

    public Mono<Void> verifyLoginCode(String email, String code, String ip) {
        return verifyCode("login", email, code, ip);
    }

    public record TestEmailCodeResponse(String code, long expiresInSeconds, long cooldownSeconds) {
    }

    public Mono<TestEmailCodeResponse> issueTestCode(String purpose, String email, String ip) {
        return issueCode(purpose, email, ip)
                .map(code -> new TestEmailCodeResponse(code, codeTtl.toSeconds(), cooldownTtl.toSeconds()));
    }

    private Mono<Void> sendCode(String purpose, String emailRaw, String ip, String subject, String bodyTpl) {
        return issueCode(purpose, emailRaw, ip)
                .flatMap(code -> mailService.sendTextWithRetry(normalizeEmail(emailRaw), subject, bodyTpl.formatted(code)));
    }

    private Mono<String> issueCode(String purpose, String emailRaw, String ip) {
        var email = normalizeEmail(emailRaw);
        if (!isEmail(email)) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        }
        return checkIpRateLimit("send:" + purpose, ip)
                .then(checkEmailRateLimit(purpose, email))
                .then(checkCooldown(purpose, email))
                .then(Mono.defer(() -> {
                    var code = randomDigits6();
                    var salt = randomSalt();
                    var hash = sha256Hex(secret + ":" + salt + ":" + code);
                    var key = KEY_CODE_PREFIX + purpose + ":" + email;
                    return redis.opsForHash()
                            .put(key, "salt", salt)
                            .then(redis.opsForHash().put(key, "hash", hash))
                            .then(redis.opsForHash().put(key, "fails", "0"))
                            .then(redis.expire(key, codeTtl))
                            .then(setCooldown(purpose, email))
                            .thenReturn(code);
                }));
    }

    private Mono<Void> verifyCode(String purpose, String emailRaw, String codeRaw, String ip) {
        var email = normalizeEmail(emailRaw);
        var code = codeRaw == null ? "" : codeRaw.trim();
        if (!isEmail(email) || code.isBlank()) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        }
        if (!code.matches("^\\d{6}$")) {
            return Mono.error(new BusinessException(ErrorCode.EMAIL_CODE_INVALID));
        }
        return checkIpRateLimit("verify:" + purpose, ip)
                .then(Mono.defer(() -> {
                    var key = KEY_CODE_PREFIX + purpose + ":" + email;
                    return redis.opsForHash()
                            .get(key, "salt")
                            .map(Object::toString)
                            .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED)))
                            .flatMap(salt -> redis.opsForHash()
                                    .get(key, "hash")
                                    .map(Object::toString)
                                    .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED)))
                                    .flatMap(hash -> {
                                        var expected = sha256Hex(secret + ":" + salt + ":" + code);
                                        if (constantTimeEquals(hash, expected)) {
                                            return redis.delete(key).then();
                                        }
                                        return redis.opsForHash()
                                                .increment(key, "fails", 1)
                                                .defaultIfEmpty(1L)
                                                .flatMap(fails -> {
                                                    if (fails >= maxAttempts) {
                                                        return redis.delete(key).then(Mono.error(new BusinessException(ErrorCode.EMAIL_CODE_TOO_MANY_ATTEMPTS)));
                                                    }
                                                    return Mono.error(new BusinessException(ErrorCode.EMAIL_CODE_INVALID));
                                                });
                                    }));
                }));
    }

    private Mono<Void> checkCooldown(String purpose, String email) {
        var key = KEY_COOLDOWN_PREFIX + purpose + ":" + email;
        return redis.hasKey(key)
                .flatMap(exists -> {
                    if (!Boolean.TRUE.equals(exists)) return Mono.empty();
                    return redis.getExpire(key)
                            .defaultIfEmpty(Duration.ZERO)
                            .flatMap(ttl -> {
                                var remain = ttl == null ? 0L : Math.max(0L, ttl.toSeconds());
                                return Mono.error(new BusinessException(ErrorCode.EMAIL_CODE_COOLDOWN, ErrorCode.EMAIL_CODE_COOLDOWN.defaultMessage(), new CooldownInfo(remain)));
                            });
                });
    }

    private Mono<Void> setCooldown(String purpose, String email) {
        var key = KEY_COOLDOWN_PREFIX + purpose + ":" + email;
        return redis.opsForValue().set(key, "1", cooldownTtl).then();
    }

    private Mono<Void> checkIpRateLimit(String scope, String ipRaw) {
        var ip = ipRaw == null ? "unknown" : ipRaw.trim();
        var key = KEY_RL_IP_PREFIX + scope + ":" + ip;
        return redis.opsForValue()
                .increment(key)
                .flatMap(v -> {
                    if (v != null && v == 1L) {
                        return redis.expire(key, Duration.ofMinutes(1)).thenReturn(v);
                    }
                    return Mono.just(v);
                })
                .flatMap(v -> {
                    if (v != null && v > ipPerMinute) {
                        return Mono.error(new BusinessException(ErrorCode.RATE_LIMITED));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> checkEmailRateLimit(String purpose, String email) {
        var key = KEY_RL_EMAIL_PREFIX + purpose + ":" + email;
        return redis.opsForValue()
                .increment(key)
                .flatMap(v -> {
                    if (v != null && v == 1L) {
                        return redis.expire(key, Duration.ofHours(1)).thenReturn(v);
                    }
                    return Mono.just(v);
                })
                .flatMap(v -> {
                    if (v != null && v > emailPerHour) {
                        return Mono.error(new BusinessException(ErrorCode.RATE_LIMITED));
                    }
                    return Mono.empty();
                });
    }

    private String normalizeEmail(String emailRaw) {
        return emailRaw == null ? "" : emailRaw.trim().toLowerCase();
    }

    private boolean isEmail(String s) {
        if (s == null) return false;
        var t = s.trim();
        if (t.isBlank()) return false;
        return t.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private String randomDigits6() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    private String randomSalt() {
        var bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
