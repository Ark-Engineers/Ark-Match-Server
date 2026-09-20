package io.arknights.dateorfriends.modules.user.lmd.service;

import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.time.Duration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** 龙门币相关接口的 Redis 频率限制（按 scope+id 每分钟计数） */
@Component
public class LmdRateLimiter {

    private static final String KEY_PREFIX = "lmd:rl:";

    private final ReactiveStringRedisTemplate redis;

    public LmdRateLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Void> check(String scope, String id, int perMinute) {
        var key = KEY_PREFIX + scope + ":" + (id == null || id.isBlank() ? "unknown" : id);
        return redis.opsForValue()
                .increment(key)
                .flatMap(v -> {
                    if (v != null && v == 1L) {
                        return redis.expire(key, Duration.ofMinutes(1)).thenReturn(v);
                    }
                    return Mono.just(v);
                })
                .flatMap(v -> {
                    if (v != null && v > perMinute) {
                        return Mono.error(new BusinessException(ErrorCode.RATE_LIMITED));
                    }
                    return Mono.empty();
                });
    }
}
