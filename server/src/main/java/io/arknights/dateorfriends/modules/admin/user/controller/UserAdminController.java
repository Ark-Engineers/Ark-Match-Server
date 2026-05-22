package io.arknights.dateorfriends.modules.admin.user.controller;

import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.security.ban.BanService;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/user")
public class UserAdminController {
    private final BanService banService;

    public UserAdminController(BanService banService) {
        this.banService = banService;
    }

    public record UserSearchResponse(
            long userId,
            String account,
            String nickname,
            String email,
            String role,
            String status,
            String lastLoginIp,
            List<String> relatedIps
    ) {
    }

    public record BanUserOpRequest(
            @Min(1) long userId,
            @Min(1) long reportId,
            String reason,
            Long durationSeconds,
            boolean confirm
    ) {
    }

    @GetMapping("/search")
    public Mono<ApiResponse<List<UserSearchResponse>>> search(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "account", required = false) String account,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "ip", required = false) String ip,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));

        return banService.assertBanPermission(principal)
                .then(banService.searchUsers(userId, account, nickname, email, ip, limit))
                .map(list -> list.stream()
                        .map(u -> new UserSearchResponse(
                                u.userId(),
                                u.account(),
                                u.nickname(),
                                u.email(),
                                u.role(),
                                u.status(),
                                u.lastLoginIp(),
                                u.relatedIps()
                        ))
                        .toList())
                .map(ApiResponse::ok);
    }

    @PostMapping("/ban/ip-only")
    public Mono<ApiResponse<List<io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordDO>>> banIpsOnly(
            @Valid @RequestBody BanUserOpRequest request,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = io.arknights.dateorfriends.tools.web.IpUtils.resolveClientIp(exchange);

        return banService.assertBanPermission(principal)
                .then(banService.banUserAssociatedIps(principal.userId(), request.userId(), request.reportId(), request.reason(), request.durationSeconds()))
                .flatMap(records -> banService.writeAdminActionLog(principal.userId(), ip, "/admin/user/ban/ip-only").thenReturn(records))
                .map(ApiResponse::ok);
    }

    @PostMapping("/ban/email-only")
    public Mono<ApiResponse<List<io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordDO>>> banEmailOnly(
            @Valid @RequestBody BanUserOpRequest request,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = io.arknights.dateorfriends.tools.web.IpUtils.resolveClientIp(exchange);

        return banService.assertBanPermission(principal)
                .then(banService.banUserEmailOnly(principal.userId(), request.userId(), request.reportId(), request.reason(), request.durationSeconds()))
                .flatMap(records -> banService.writeAdminActionLog(principal.userId(), ip, "/admin/user/ban/email-only").thenReturn(records))
                .map(ApiResponse::ok);
    }

    @PostMapping("/ban/full")
    public Mono<ApiResponse<List<io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordDO>>> banFull(
            @Valid @RequestBody BanUserOpRequest request,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = io.arknights.dateorfriends.tools.web.IpUtils.resolveClientIp(exchange);

        return banService.assertBanPermission(principal)
                .then(banService.banUserFull(principal.userId(), request.userId(), request.reportId(), request.reason(), request.durationSeconds(), request.confirm()))
                .flatMap(records -> banService.writeAdminActionLog(principal.userId(), ip, "/admin/user/ban/full").thenReturn(records))
                .map(ApiResponse::ok);
    }
}
