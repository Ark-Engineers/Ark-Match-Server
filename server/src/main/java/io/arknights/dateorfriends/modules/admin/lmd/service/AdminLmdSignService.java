package io.arknights.dateorfriends.modules.admin.lmd.service;

import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 管理员龙门币写操作签名校验：
 * canonical = scope|field1|field2|...|ts|nonce（字段顺序与前端约定一致），
 * sign = HMAC-SHA256(secret, canonical) 十六进制小写；
 * 校验时间戳窗口 + nonce 防重放（Redis 一次性）。
 * 服务端未配置密钥时全部拒绝（fail-closed）。
 */
@Component
public class AdminLmdSignService {

    private static final String NONCE_KEY_PREFIX = "lmd:sign-nonce:";

    private final ReactiveStringRedisTemplate redis;
    private final String secret;
    private final long tsWindowSeconds;

    public AdminLmdSignService(
            ReactiveStringRedisTemplate redis,
            @Value("${app.lmd.sign-secret:}") String secret,
            @Value("${app.lmd.sign-ts-window-seconds:300}") long tsWindowSeconds
    ) {
        this.redis = redis;
        this.secret = secret == null ? "" : secret;
        this.tsWindowSeconds = Math.max(60, tsWindowSeconds);
    }

    public String canonicalForAdjust(long userId, long amount, String description, long ts, String nonce) {
        return "lmd.adjust|" + userId + "|" + amount + "|" + nvl(description) + "|" + ts + "|" + nonce;
    }

    public String canonicalForSetBalance(long userId, long balance, long expectedBalance, String description, long ts, String nonce) {
        return "lmd.set-balance|" + userId + "|" + balance + "|" + expectedBalance + "|" + nvl(description).trim() + "|" + ts + "|" + nonce;
    }

    public String canonicalForPublish(String title, String content, long lmdAmount, String claimExpireAt, long ts, String nonce) {
        return "lmd.mail.publish|" + nvl(title) + "|" + nvl(content) + "|" + lmdAmount + "|" + nvl(claimExpireAt) + "|" + ts + "|" + nonce;
    }

    public Mono<Void> verify(String canonical, long ts, String nonce, String sign) {
        if (secret.isBlank()) {
            return Mono.error(new BusinessException(ErrorCode.LMD_SIGN_INVALID, "服务端未配置 app.lmd.sign-secret，签名接口不可用"));
        }
        if (Math.abs(System.currentTimeMillis() / 1000L - ts) > tsWindowSeconds) {
            return Mono.error(new BusinessException(ErrorCode.LMD_SIGN_INVALID, "请求时间戳超出允许窗口"));
        }
        if (nonce == null || nonce.isBlank() || sign == null || sign.isBlank()) {
            return Mono.error(new BusinessException(ErrorCode.LMD_SIGN_INVALID));
        }
        var expected = hmacSha256Hex(secret, canonical);
        if (!constantTimeEquals(expected, sign)) {
            return Mono.error(new BusinessException(ErrorCode.LMD_SIGN_INVALID));
        }
        return redis.opsForValue()
                .setIfAbsent(NONCE_KEY_PREFIX + nonce, "1", Duration.ofSeconds(tsWindowSeconds * 2L))
                .flatMap(ok -> Boolean.TRUE.equals(ok)
                        ? Mono.empty()
                        : Mono.error(new BusinessException(ErrorCode.LMD_SIGN_INVALID, "重复请求（nonce 已被使用）")));
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }

    private String hmacSha256Hex(String key, String message) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.LMD_SIGN_INVALID, "签名计算失败");
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
