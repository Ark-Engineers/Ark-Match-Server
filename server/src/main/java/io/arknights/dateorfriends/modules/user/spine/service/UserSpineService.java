package io.arknights.dateorfriends.modules.user.spine.service;

import io.arknights.dateorfriends.modules.admin.spine.mapper.SpineAssetMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class UserSpineService {

    public record SpineOption(String assetKey, String name, String skelUrl) {
    }

    private final SpineAssetMapper spineAssetMapper;

    public UserSpineService(SpineAssetMapper spineAssetMapper) {
        this.spineAssetMapper = spineAssetMapper;
    }

    public Mono<List<SpineOption>> listOptions() {
        return Mono.fromCallable(() -> spineAssetMapper.list(null, 1, 0, 200).stream()
                        .map(a -> new SpineOption(
                                a.getAssetKey(),
                                a.getName(),
                                "/assets/spine/" + a.getAssetKey() + "/" + a.getAssetKey() + ".skel"))
                        .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }
}

