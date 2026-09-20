package io.arknights.dateorfriends.modules.user.lmd.controller;

import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdTransactionMapper;
import io.arknights.dateorfriends.modules.user.lmd.service.LmdClaimService;
import io.arknights.dateorfriends.modules.user.lmd.service.LmdWalletService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import io.arknights.dateorfriends.tools.web.TraceWebFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 龙门币用户端接口：余额查询、个人流水、邮件领取凭证与领取（与通知模块解耦的独立接口） */
@RestController
@RequestMapping("/user/lmd")
public class UserLmdController {

    private final LmdWalletService walletService;
    private final LmdTransactionMapper txMapper;
    private final LmdClaimService claimService;

    public UserLmdController(LmdWalletService walletService, LmdTransactionMapper txMapper, LmdClaimService claimService) {
        this.walletService = walletService;
        this.txMapper = txMapper;
        this.claimService = claimService;
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public record BalanceResponse(long balance) {
    }

    public record UserTxItem(
            long id,
            long amount,
            long balanceAfter,
            String type,
            String refType,
            Long refId,
            String description,
            LocalDateTime createdAt
    ) {
    }

    public record ClaimTicketRequest(@Min(1) long notificationId) {
    }

    public record ClaimRequest(@Min(1) long notificationId, @NotBlank String ticket) {
    }

    @GetMapping("/balance")
    public Mono<ApiResponse<BalanceResponse>> balance(ServerWebExchange exchange) {
        var principal = requirePrincipal(exchange);
        return Mono.fromCallable(() -> new BalanceResponse(walletService.getBalance(principal.userId())))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @GetMapping("/transactions")
    public Mono<ApiResponse<PageResponse<UserTxItem>>> transactions(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;
        var safeType = type == null || type.isBlank() ? null : type.trim();
        return Mono.fromCallable(() -> {
                    var total = txMapper.countByUser(principal.userId(), safeType);
                    var items = txMapper.selectListByUser(principal.userId(), safeType, safeSize, offset).stream()
                            .map(i -> new UserTxItem(
                                    i.getId() == null ? 0 : i.getId(),
                                    i.getAmount() == null ? 0 : i.getAmount(),
                                    i.getBalanceAfter() == null ? 0 : i.getBalanceAfter(),
                                    i.getType(),
                                    i.getRefType(),
                                    i.getRefId(),
                                    i.getDescription(),
                                    i.getCreatedAt()
                            ))
                            .toList();
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @PostMapping("/mail/claim-ticket")
    public Mono<ApiResponse<LmdClaimService.TicketResponse>> claimTicket(
            @Valid @RequestBody ClaimTicketRequest req,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        var ip = IpUtils.resolveClientIp(exchange);
        return claimService.issueTicket(principal.userId(), req.notificationId(), ip).map(ApiResponse::ok);
    }

    @PostMapping("/mail/claim")
    public Mono<ApiResponse<LmdClaimService.ClaimResponse>> claim(
            @Valid @RequestBody ClaimRequest req,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        var ip = IpUtils.resolveClientIp(exchange);
        var traceId = exchange.getAttributeOrDefault(TraceWebFilter.ATTR_TRACE_ID, "");
        return claimService.claim(principal.userId(), req.notificationId(), req.ticket().trim(), traceId, ip)
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
