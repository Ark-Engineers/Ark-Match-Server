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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    public record RoundControlRequest(@Min(1) long roundId) {
    }

    public record EndRoundRequest(@Min(1) long roundId, @NotBlank String expectedStatus) {
    }

    public record RankingRequest(
            @Min(1) long roundId,
            @NotNull @Size(min = 5, max = 5) List<@NotNull @Min(1) Long> participantIds
    ) {
    }

    public record NextParticipantsRequest(
            @Min(1) long roundId,
            @NotNull @Size(max = 5) List<@NotNull @Min(1) Long> assetIds
    ) {
    }

    /** 仅 SUPER_ADMIN 查询控制状态；仅演示场返回预设名次，普通竞猜不提前公开排名。 */
    @GetMapping("/{id}/developer")
    public Mono<ApiResponse<RaceEngineService.DeveloperState>> developer(
            @PathVariable("id") long id, ServerWebExchange exchange
    ) {
        requireSuperAdmin(exchange);
        return Mono.fromCallable(() -> engine.developerState(id))
                .subscribeOn(Schedulers.boundedElastic()).map(ApiResponse::ok);
    }

    /** 仅 SUPER_ADMIN 立即开赛或跳过赛前等待；绑定当前轮次，保留已有下注和确定的名次。 */
    @PostMapping("/{id}/developer/start")
    public Mono<ApiResponse<Boolean>> startNow(
            @PathVariable("id") long id, @Valid @RequestBody RoundControlRequest req,
            ServerWebExchange exchange
    ) {
        var admin = requireSuperAdmin(exchange);
        return Mono.fromCallable(() -> {
            engine.startNow(admin.userId(), id, req.roundId());
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).map(ApiResponse::ok);
    }

    /** 仅 SUPER_ADMIN 指定完整名次；仅无人下注的竞猜阶段允许，保存后本轮禁止下注。 */
    @PostMapping("/{id}/developer/ranking")
    public Mono<ApiResponse<Boolean>> setRanking(
            @PathVariable("id") long id, @Valid @RequestBody RankingRequest req,
            ServerWebExchange exchange
    ) {
        var admin = requireSuperAdmin(exchange);
        return Mono.fromCallable(() -> {
            engine.setRanking(admin.userId(), id, req.roundId(), req.participantIds());
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).map(ApiResponse::ok);
    }

    /** 仅 SUPER_ADMIN 指定下一轮五名敌人或Boss；空数组清除计划，最后一轮不可设置。 */
    @PostMapping("/{id}/developer/next-participants")
    public Mono<ApiResponse<Boolean>> nextParticipants(
            @PathVariable("id") long id, @Valid @RequestBody NextParticipantsRequest req,
            ServerWebExchange exchange
    ) {
        var admin = requireSuperAdmin(exchange);
        return Mono.fromCallable(() -> {
            engine.setNextParticipants(admin.userId(), id, req.roundId(), req.assetIds());
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).map(ApiResponse::ok);
    }

    /** 仅 SUPER_ADMIN 提前结算或结束领奖台；同时校验轮次与确认时阶段，禁止跨阶段重复推进。 */
    @PostMapping("/{id}/developer/end")
    public Mono<ApiResponse<Boolean>> endNow(
            @PathVariable("id") long id, @Valid @RequestBody EndRoundRequest req,
            ServerWebExchange exchange
    ) {
        var admin = requireSuperAdmin(exchange);
        return Mono.fromCallable(() -> {
            engine.endNow(admin.userId(), id, req.roundId(), req.expectedStatus());
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).map(ApiResponse::ok);
    }

    private JwtPrincipal requireSuperAdmin(ServerWebExchange exchange) {
        var principal = requireAdmin(exchange);
        if (!"SUPER_ADMIN".equalsIgnoreCase(principal.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return principal;
    }

    private JwtPrincipal requireAdmin(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!"ADMIN".equalsIgnoreCase(principal.role()) && !"SUPER_ADMIN".equalsIgnoreCase(principal.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return principal;
    }
}
