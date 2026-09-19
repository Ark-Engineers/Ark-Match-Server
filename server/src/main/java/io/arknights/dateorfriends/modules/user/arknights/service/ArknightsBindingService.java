package io.arknights.dateorfriends.modules.user.arknights.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import reactor.core.publisher.Mono;

public interface ArknightsBindingService {

    record OfficialBasic(
            @NotNull Boolean isMinor,
            @NotBlank @Size(max = 64) String hgId
    ) {
    }

    record OfficialAccountBinding(
            @NotBlank @Size(max = 64) String uid,
            @NotBlank @Size(max = 128) String nickName,
            @NotBlank @Size(max = 64) String channelName
    ) {
    }

    record BindRequest(
            @NotNull @Valid OfficialBasic basic,
            @NotNull @Valid OfficialAccountBinding accountBinding
    ) {
    }

    record BindingStatus(
            boolean bound,
            Boolean isMinor,
            Boolean isAdult,
            String hgId,
            String uid,
            String nickName,
            String channelName,
            LocalDateTime boundAt
    ) {
    }

    Mono<BindingStatus> getStatus(long userId);

    Mono<BindingStatus> bind(long userId, BindRequest request);

    Mono<Void> unbind(long userId);
}
