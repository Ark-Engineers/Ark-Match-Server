package io.arknights.dateorfriends.modules.user.profile.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ArknightsAvatarService {

    private final WebClient webClient;

    public ArknightsAvatarService() {
        this.webClient = WebClient.builder().build();
    }

    public record AvatarOption(
            String id,
            String name,
            Integer rarity,
            String avatarUrl
    ) {
    }

    public Mono<List<AvatarOption>> listOptions() {
        return webClient.get()
                .uri("https://zonai.skland.com/h5/v1/game/arknights/char-book/list")
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::toOptions)
                .onErrorReturn(List.of());
    }

    private List<AvatarOption> toOptions(Map root) {
        if (root == null) return List.of();
        var data = root.get("data");
        if (!(data instanceof Map)) return List.of();
        var characters = ((Map) data).get("characters");
        if (!(characters instanceof List)) return List.of();

        var out = new ArrayList<AvatarOption>();
        for (var item : (List) characters) {
            if (!(item instanceof Map)) continue;
            var m = (Map) item;
            var id = trimToNull(m.get("id"));
            var name = trimToNull(m.get("name"));
            if (id == null || name == null) continue;
            var rarity = toInt(m.get("rarity"));
            out.add(new AvatarOption(id, name, rarity, buildAvatarUrl(id)));
        }

        out.sort(Comparator
                .comparing((AvatarOption o) -> o.rarity() == null ? -1 : o.rarity()).reversed()
                .thenComparing(o -> o.name() == null ? "" : o.name()));

        return out;
    }

    private String buildAvatarUrl(String id) {
        return "https://web.hycdn.cn/arknights/game/assets/char/avatar/" + id + ".png";
    }

    private String trimToNull(Object v) {
        if (v == null) return null;
        var s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }
}

