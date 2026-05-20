package io.arknights.dateorfriends.tools.security.token;

import java.time.Duration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisTokenStore {

    private static final String KEY_PREFIX_BLACKLIST = "auth:blacklist:";
    private static final String KEY_PREFIX_TOKEN_VERSION = "auth:ver:";
    private static final String KEY_PREFIX_REFRESH_SET = "auth:refresh:";

    private final ReactiveStringRedisTemplate redis;

    public RedisTokenStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Boolean> isBlacklisted(String jti) {
        return redis.hasKey(KEY_PREFIX_BLACKLIST + jti).defaultIfEmpty(false);
    }

    public Mono<Void> blacklist(String jti, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return Mono.empty();
        }
        var key = KEY_PREFIX_BLACKLIST + jti;
        return redis.opsForValue().set(key, "1", ttl).then();
    }

    public Mono<Long> getTokenVersion(long userId) {
        return redis.opsForValue()
                .get(KEY_PREFIX_TOKEN_VERSION + userId)
                .map(Long::parseLong)
                .defaultIfEmpty(1L);
    }

    public Mono<Long> bumpTokenVersion(long userId) {
        return redis.opsForValue()
                .increment(KEY_PREFIX_TOKEN_VERSION + userId)
                .defaultIfEmpty(1L);
    }

    public Mono<Boolean> isRefreshTokenValid(long userId, String jti) {
        return redis.opsForSet().isMember(KEY_PREFIX_REFRESH_SET + userId, jti).defaultIfEmpty(false);
    }

    public Mono<Void> storeRefreshToken(long userId, String jti, Duration ttl) {
        var key = KEY_PREFIX_REFRESH_SET + userId;
        return redis.opsForSet()
                .add(key, jti)
                .then(redis.expire(key, ttl))
                .then();
    }

    public Mono<Void> revokeRefreshToken(long userId, String jti) {
        return redis.opsForSet().remove(KEY_PREFIX_REFRESH_SET + userId, jti).then();
    }

    public Mono<Void> revokeAllRefreshTokens(long userId) {
        return redis.delete(KEY_PREFIX_REFRESH_SET + userId).then();
    }
}

