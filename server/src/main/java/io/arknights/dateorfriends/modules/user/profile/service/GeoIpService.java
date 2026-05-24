package io.arknights.dateorfriends.modules.user.profile.service;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class GeoIpService {

    private static final String KEY_PREFIX = "geo:amap:";

    private final ReactiveStringRedisTemplate redis;
    private final WebClient webClient;
    private final String amapKey;

    public GeoIpService(
            ReactiveStringRedisTemplate redis,
            @Value("${app.geo.amap.key:}") String amapKey
    ) {
        this.redis = redis;
        this.webClient = WebClient.builder().build();
        this.amapKey = amapKey == null ? "" : amapKey.trim();
    }

    public Mono<String> resolveProvinceCityByIp(String ipRaw) {
        var ip = ipRaw == null ? "" : ipRaw.trim();
        if (ip.isBlank()) return Mono.just("未知");
        if (amapKey.isBlank()) return Mono.just("未知");

        var key = KEY_PREFIX + ip;
        return redis.opsForValue()
                .get(key)
                .filter(v -> v != null && !v.isBlank())
                .switchIfEmpty(Mono.defer(() -> fetchAndCache(ip, key)));
    }

    private Mono<String> fetchAndCache(String ip, String cacheKey) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("restapi.amap.com")
                        .path("/v3/ip")
                        .queryParam("ip", ip)
                        .queryParam("key", amapKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::formatProvinceCity)
                .onErrorReturn("未知")
                .flatMap(city -> redis.opsForValue().set(cacheKey, city, Duration.ofDays(7)).thenReturn(city));
    }

    private String formatProvinceCity(Map data) {
        if (data == null) return "未知";
        var province = valueOf(data.get("province"));
        var city = valueOf(data.get("city"));
        if ("[]".equals(city)) city = "";
        if (province.isBlank() && city.isBlank()) return "未知";
        if (city.isBlank()) return province;
        return province + city;
    }

    private String valueOf(Object v) {
        if (v == null) return "";
        var s = String.valueOf(v).trim();
        return s;
    }
}

