package io.arknights.dateorfriends.tools.security.ban;

import java.time.Duration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisBanStore {
    private static final String KEY_PREFIX_IP = "ban:ip:";
    private static final String KEY_PREFIX_EMAIL = "ban:email:";
    private static final String KEY_PREFIX_USER = "ban:user:";

    private final ReactiveStringRedisTemplate redis;

    public RedisBanStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Boolean> isIpBanned(String ip) {
        if (ip == null || ip.isBlank()) return Mono.just(false);
        return redis.hasKey(KEY_PREFIX_IP + ip).defaultIfEmpty(false);
    }

    public Mono<Boolean> isEmailBanned(String email) {
        if (email == null || email.isBlank()) return Mono.just(false);
        return redis.hasKey(KEY_PREFIX_EMAIL + email.toLowerCase()).defaultIfEmpty(false);
    }

    public Mono<Void> banIp(String ip, Duration ttl) {
        if (ip == null || ip.isBlank()) return Mono.empty();
        var key = KEY_PREFIX_IP + ip;
        if (ttl == null) {
            return redis.opsForValue().set(key, "1").then();
        }
        if (ttl.isNegative() || ttl.isZero()) return Mono.empty();
        return redis.opsForValue().set(key, "1", ttl).then();
    }

    public Mono<Void> banEmail(String email, Duration ttl) {
        if (email == null || email.isBlank()) return Mono.empty();
        var key = KEY_PREFIX_EMAIL + email.toLowerCase();
        if (ttl == null) {
            return redis.opsForValue().set(key, "1").then();
        }
        if (ttl.isNegative() || ttl.isZero()) return Mono.empty();
        return redis.opsForValue().set(key, "1", ttl).then();
    }

    public Mono<Boolean> isUserBanned(long userId) {
        if (userId <= 0) return Mono.just(false);
        return redis.hasKey(KEY_PREFIX_USER + userId).defaultIfEmpty(false);
    }

    public Mono<Void> banUser(long userId, Duration ttl) {
        if (userId <= 0) return Mono.empty();
        var key = KEY_PREFIX_USER + userId;
        if (ttl == null) {
            return redis.opsForValue().set(key, "1").then();
        }
        if (ttl.isNegative() || ttl.isZero()) return Mono.empty();
        return redis.opsForValue().set(key, "1", ttl).then();
    }

    public Mono<Void> unbanUser(long userId) {
        if (userId <= 0) return Mono.empty();
        return redis.delete(KEY_PREFIX_USER + userId).then();
    }

    public Mono<Void> unbanIp(String ip) {
        if (ip == null || ip.isBlank()) return Mono.empty();
        return redis.delete(KEY_PREFIX_IP + ip).then();
    }

    public Mono<Void> unbanEmail(String email) {
        if (email == null || email.isBlank()) return Mono.empty();
        return redis.delete(KEY_PREFIX_EMAIL + email.toLowerCase()).then();
    }
}
