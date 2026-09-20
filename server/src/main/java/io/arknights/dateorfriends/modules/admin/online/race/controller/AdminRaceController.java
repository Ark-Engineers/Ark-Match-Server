package io.arknights.dateorfriends.modules.admin.online.race.controller;

import io.arknights.dateorfriends.modules.user.online.race.service.RaceEngineService;
import io.arknights.dateorfriends.modules.user.online.race.service.RaceVerifyService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 赛马竞猜管理端接口（/admin/** 由 AuthWebFilter 统一校验 ADMIN/SUPER_ADMIN 角色） */
@RestController
@RequestMapping("/admin/online/race")
public class AdminRaceController {

    private final RaceEngineService engine;
    private final RaceVerifyService verifyService;

    public AdminRaceController(RaceEngineService engine, RaceVerifyService verifyService) {
        this.engine = engine;
        this.verifyService = verifyService;
    }

    public record CreateRequest(
            @NotBlank String roomId,
            String name,
            int sessionType,
            Integer totalRounds,
            int participantMode,
            List<Long> participantAssetIds,
            Long betStartAtMs,
            Long betEndAtMs
    ) {
    }

    @GetMapping("/catalog")
    public Mono<ApiResponse<List<RaceEngineService.AssetOption>>> catalog(ServerWebExchange exchange) {
        requireAdmin(exchange);
        return Mono.fromCallable(engine::catalog)
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @GetMapping("/list")
    public Mono<ApiResponse<List<RaceEngineService.RaceAdminRow>>> list(ServerWebExchange exchange) {
        requireAdmin(exchange);
        return Mono.fromCallable(engine::listActiveRows)
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    /** 创建赛马模式：仅 ADMIN/SUPER_ADMIN；sessionType=3（无限循环）无需指定竞猜起止时间，创建后立即开始 */
    @PostMapping("/create")
    public Mono<ApiResponse<Long>> create(
            @Valid @RequestBody CreateRequest req,
            ServerWebExchange exchange
    ) {
        var admin = requireAdmin(exchange);
        var mapped = new RaceEngineService.CreateRequest(
                req.roomId().trim(),
                req.name(),
                req.sessionType(),
                req.totalRounds(),
                req.participantMode(),
                req.participantAssetIds(),
                req.betStartAtMs(),
                req.betEndAtMs()
        );
        return Mono.fromCallable(() -> engine.createRace(admin.userId(), mapped))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @PostMapping("/{id}/close")
    public Mono<ApiResponse<Boolean>> close(
            @PathVariable("id") long id,
            ServerWebExchange exchange
    ) {
        var admin = requireAdmin(exchange);
        return Mono.fromCallable(() -> {
                    engine.closeRace(admin.userId(), id);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<RaceEngineService.AdminDetail>> detail(
            @PathVariable("id") long id,
            ServerWebExchange exchange
    ) {
        requireAdmin(exchange);
        return Mono.fromCallable(() -> engine.detail(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @PostMapping("/round/{roundId}/verify")
    public Mono<ApiResponse<RaceVerifyService.VerifyResult>> verify(
            @PathVariable("roundId") long roundId,
            ServerWebExchange exchange
    ) {
        requireAdmin(exchange);
        return Mono.fromCallable(() -> verifyService.verifyRound(roundId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    private JwtPrincipal requireAdmin(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
