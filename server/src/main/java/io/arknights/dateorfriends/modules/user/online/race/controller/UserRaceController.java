package io.arknights.dateorfriends.modules.user.online.race.controller;

import io.arknights.dateorfriends.modules.user.lmd.service.LmdRateLimiter;
import io.arknights.dateorfriends.modules.user.online.race.service.RaceEngineService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 赛马竞猜用户端接口：房间赛事状态查询、下注（仅支持房间内成员，龙门币结算） */
@RestController
@RequestMapping("/user/online/race")
public class UserRaceController {

    private final RaceEngineService engine;
    private final LmdRateLimiter rateLimiter;

    public UserRaceController(RaceEngineService engine, LmdRateLimiter rateLimiter) {
        this.engine = engine;
        this.rateLimiter = rateLimiter;
    }

    public record BetRequest(
            @NotBlank String roomId,
            @Min(1) long participantId,
            @Min(1) long amount
    ) {
    }

    @GetMapping("/state")
    public Mono<ApiResponse<RaceEngineService.StateResponse>> state(
            @RequestParam("roomId") String roomId,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        return Mono.fromCallable(() -> engine.getState(principal.userId(), roomId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @PostMapping("/bet")
    public Mono<ApiResponse<RaceEngineService.BetResult>> bet(
            @Valid @RequestBody BetRequest req,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        var ip = IpUtils.resolveClientIp(exchange);
        return rateLimiter.check("race-bet", String.valueOf(principal.userId()), 12)
                .then(Mono.fromCallable(() -> engine.placeBet(
                        principal.userId(), req.roomId().trim(), req.participantId(), req.amount(), ip
                )))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    private JwtPrincipal requirePrincipal(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
