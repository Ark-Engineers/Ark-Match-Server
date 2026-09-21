package io.arknights.dateorfriends.modules.admin.lmd.controller;

import io.arknights.dateorfriends.modules.admin.lmd.service.AdminLmdSignService;
import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdMailClaimMapper;
import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdTransactionMapper;
import io.arknights.dateorfriends.modules.user.lmd.service.LmdRateLimiter;
import io.arknights.dateorfriends.modules.user.lmd.service.LmdVerifyService;
import io.arknights.dateorfriends.modules.user.lmd.service.LmdWalletService;
import io.arknights.dateorfriends.modules.user.notification.service.SiteNotificationService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.security.Role;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import io.arknights.dateorfriends.tools.web.TraceWebFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 龙门币管理端接口（/admin/** 由 AuthWebFilter 统一校验 ADMIN/SUPER_ADMIN 角色）：
 * 写操作（调整/发布）额外要求 HMAC 签名 + 时间戳窗口 + nonce 防重放 + 频率限制；
 * 读操作（审计/校验）要求频率限制。所有流水落库均带 traceId 与来源 IP 溯源。
 */
@RestController("adminLmdController")
@RequestMapping("/admin/lmd")
public class AdminLmdController {

    private static final DateTimeFormatter CLAIM_EXPIRE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_SINGLE_AMOUNT = 10_000_000L;
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private final LmdWalletService walletService;
    private final LmdTransactionMapper txMapper;
    private final LmdMailClaimMapper claimMapper;
    private final AdminLmdSignService signService;
    private final LmdRateLimiter rateLimiter;
    private final SiteNotificationService notificationService;
    private final LmdVerifyService verifyService;
    private final UserMapper userMapper;

    private final int adjustPerMinute;
    private final int publishPerMinute;
    private final int auditPerMinute;

    public AdminLmdController(
            LmdWalletService walletService,
            LmdTransactionMapper txMapper,
            LmdMailClaimMapper claimMapper,
            AdminLmdSignService signService,
            LmdRateLimiter rateLimiter,
            SiteNotificationService notificationService,
            LmdVerifyService verifyService,
            UserMapper userMapper,
            @Value("${app.lmd.rate.adjust-per-minute:30}") int adjustPerMinute,
            @Value("${app.lmd.rate.publish-per-minute:10}") int publishPerMinute,
            @Value("${app.lmd.rate.audit-per-minute:60}") int auditPerMinute
    ) {
        this.walletService = walletService;
        this.txMapper = txMapper;
        this.claimMapper = claimMapper;
        this.signService = signService;
        this.rateLimiter = rateLimiter;
        this.notificationService = notificationService;
        this.verifyService = verifyService;
        this.userMapper = userMapper;
        this.adjustPerMinute = Math.max(1, adjustPerMinute);
        this.publishPerMinute = Math.max(1, publishPerMinute);
        this.auditPerMinute = Math.max(1, auditPerMinute);
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public record AdminTxItem(
            long id,
            long userId,
            String account,
            String nickname,
            long amount,
            long balanceAfter,
            String type,
            String refType,
            Long refId,
            String description,
            String traceId,
            String requestIp,
            Long createdBy,
            LocalDateTime createdAt
    ) {
    }

    public record AdjustRequest(
            @Min(1) long userId,
            long amount,
            String description,
            long ts,
            String nonce,
            String sign
    ) {
    }

    public record AdjustResponse(long userId, long amount, long balanceAfter) {
    }

    public record SetBalanceRequest(
            @Min(1) @Max(MAX_SAFE_INTEGER) long userId,
            @NotNull @Min(0) @Max(MAX_SAFE_INTEGER) Long balance,
            @NotNull @Min(0) @Max(MAX_SAFE_INTEGER) Long expectedBalance,
            String description,
            long ts,
            String nonce,
            String sign
    ) {
    }

    public record PublishRequest(
            String title,
            String content,
            String level,
            long lmdAmount,
            String claimExpireAt,
            long ts,
            String nonce,
            String sign
    ) {
    }

    public record PublishResponse(long notificationId, long deliveredCount) {
    }

    public record AdminClaimItem(
            long id,
            long notificationId,
            String mailTitle,
            long userId,
            String account,
            String nickname,
            long amount,
            String traceId,
            String requestIp,
            LocalDateTime createdAt
    ) {
    }

    @GetMapping("/transactions")
    public Mono<ApiResponse<PageResponse<AdminTxItem>>> transactions(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "refType", required = false) String refType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;
        var safeType = blankToNull(type);
        var safeRefType = blankToNull(refType);
        return rateLimiter.check("admin-audit", String.valueOf(principal.userId()), auditPerMinute)
                .then(Mono.fromCallable(() -> {
                    var total = txMapper.countForAdmin(userId, safeType, safeRefType);
                    var items = txMapper.selectListForAdmin(userId, safeType, safeRefType, safeSize, offset).stream()
                            .map(i -> new AdminTxItem(
                                    i.getId() == null ? 0 : i.getId(),
                                    i.getUserId() == null ? 0 : i.getUserId(),
                                    i.getAccount(),
                                    i.getNickname(),
                                    i.getAmount() == null ? 0 : i.getAmount(),
                                    i.getBalanceAfter() == null ? 0 : i.getBalanceAfter(),
                                    i.getType(),
                                    i.getRefType(),
                                    i.getRefId(),
                                    i.getDescription(),
                                    i.getTraceId(),
                                    i.getRequestIp(),
                                    i.getCreatedBy(),
                                    i.getCreatedAt()
                            ))
                            .toList();
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic()))
                .map(ApiResponse::ok);
    }

    @PostMapping("/adjust")
    public Mono<ApiResponse<AdjustResponse>> adjust(@Valid @RequestBody AdjustRequest req, ServerWebExchange exchange) {
        var principal = requirePrincipal(exchange);
        if (req.amount() == 0 || req.amount() < -MAX_SINGLE_AMOUNT || req.amount() > MAX_SINGLE_AMOUNT) {
            return Mono.error(new BusinessException(ErrorCode.LMD_AMOUNT_INVALID,
                    "额度必须为非零且绝对值不超过 " + MAX_SINGLE_AMOUNT));
        }
        var description = req.description() == null ? null : req.description().trim();
        if (description != null && description.length() > 255) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "备注过长"));
        }
        var canonical = signService.canonicalForAdjust(req.userId(), req.amount(), description, req.ts(), req.nonce());
        return rateLimiter.check("admin-adjust", String.valueOf(principal.userId()), adjustPerMinute)
                .then(signService.verify(canonical, req.ts(), req.nonce(), req.sign()))
                .then(Mono.fromCallable(() -> {
                            assertCanOperateTarget(principal, req.userId());
                            var ip = IpUtils.resolveClientIp(exchange);
                            var traceId = traceOf(exchange);
                            var result = walletService.adjustBalance(
                                    req.userId(), req.amount(), description, traceId, ip, principal.userId());
                            return new AdjustResponse(req.userId(), result.amount(), result.balanceAfter());
                        })
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(ApiResponse::ok);
    }

    /** 设置用户龙门币余额，校验预期余额后以 ADMIN_ADJUST 差额流水记账。 */
    @PostMapping("/set-balance")
    public Mono<ApiResponse<AdjustResponse>> setBalance(@Valid @RequestBody SetBalanceRequest req, ServerWebExchange exchange) {
        var principal = requirePrincipal(exchange);
        var description = req.description() == null ? null : req.description().trim();
        if (description != null && description.length() > 255) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "备注过长"));
        }
        var canonical = signService.canonicalForSetBalance(
                req.userId(), req.balance(), req.expectedBalance(), description, req.ts(), req.nonce());
        return rateLimiter.check("admin-adjust", String.valueOf(principal.userId()), adjustPerMinute)
                .then(signService.verify(canonical, req.ts(), req.nonce(), req.sign()))
                .then(Mono.fromCallable(() -> {
                            assertCanOperateTarget(principal, req.userId());
                            var ip = IpUtils.resolveClientIp(exchange);
                            var traceId = traceOf(exchange);
                            var result = walletService.setBalance(
                                    req.userId(), req.balance(), req.expectedBalance(), description, traceId, ip, principal.userId());
                            return new AdjustResponse(req.userId(), result.amount(), result.balanceAfter());
                        })
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(ApiResponse::ok);
    }

    @PostMapping("/mail/publish")
    public Mono<ApiResponse<PublishResponse>> publish(@Valid @RequestBody PublishRequest req, ServerWebExchange exchange) {
        var principal = requirePrincipal(exchange);
        var title = req.title() == null ? "" : req.title().trim();
        var content = req.content() == null ? "" : req.content().trim();
        if (title.isBlank() || title.length() > 128) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "邮件标题必填且不超过128字"));
        }
        if (content.isBlank() || content.length() > 5000) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "邮件内容必填且不超过5000字"));
        }
        if (req.lmdAmount() <= 0 || req.lmdAmount() > MAX_SINGLE_AMOUNT) {
            return Mono.error(new BusinessException(ErrorCode.LMD_AMOUNT_INVALID,
                    "龙门币额度必须在 1 ~ " + MAX_SINGLE_AMOUNT + " 之间"));
        }
        var level = req.level() == null || req.level().isBlank() ? "IMPORTANT" : req.level().trim().toUpperCase();
        if (!"NORMAL".equals(level) && !"IMPORTANT".equals(level)) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "邮件等级不合法"));
        }
        var expireAt = parseClaimExpire(req.claimExpireAt());
        if (expireAt != null && !expireAt.isAfter(LocalDateTime.now())) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "领取截止时间必须晚于当前时间"));
        }
        var canonical = signService.canonicalForPublish(title, content, req.lmdAmount(), req.claimExpireAt(), req.ts(), req.nonce());
        return rateLimiter.check("admin-publish", String.valueOf(principal.userId()), publishPerMinute)
                .then(signService.verify(canonical, req.ts(), req.nonce(), req.sign()))
                .then(notificationService.sendToAllUsers(
                        principal.userId(), "SYSTEM", title, content, level, null, null, req.lmdAmount(), expireAt))
                .map(r -> ApiResponse.ok(new PublishResponse(r.notificationId(), r.deliveredCount())));
    }

    @GetMapping("/mail/claims")
    public Mono<ApiResponse<PageResponse<AdminClaimItem>>> claims(
            @RequestParam(value = "notificationId", required = false) Long notificationId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;
        return rateLimiter.check("admin-audit", String.valueOf(principal.userId()), auditPerMinute)
                .then(Mono.fromCallable(() -> {
                    var total = claimMapper.countForAdmin(notificationId, userId);
                    var items = claimMapper.selectListForAdmin(notificationId, userId, safeSize, offset).stream()
                            .map(i -> new AdminClaimItem(
                                    i.getId() == null ? 0 : i.getId(),
                                    i.getNotificationId() == null ? 0 : i.getNotificationId(),
                                    i.getMailTitle(),
                                    i.getUserId() == null ? 0 : i.getUserId(),
                                    i.getAccount(),
                                    i.getNickname(),
                                    i.getAmount() == null ? 0 : i.getAmount(),
                                    i.getTraceId(),
                                    i.getRequestIp(),
                                    i.getCreatedAt()
                            ))
                            .toList();
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic()))
                .map(ApiResponse::ok);
    }

    @GetMapping("/verify")
    public Mono<ApiResponse<LmdVerifyService.VerifyResult>> verify(
            @RequestParam(value = "userId", required = false) Long userId,
            ServerWebExchange exchange
    ) {
        var principal = requirePrincipal(exchange);
        return rateLimiter.check("admin-audit", String.valueOf(principal.userId()), auditPerMinute)
                .then(userId == null ? verifyService.verifyAll() : verifyService.verifyOne(userId))
                .map(ApiResponse::ok);
    }

    private LocalDateTime parseClaimExpire(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw.trim(), CLAIM_EXPIRE_FORMAT);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "领取截止时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private String traceOf(ServerWebExchange exchange) {
        return exchange.getAttributeOrDefault(TraceWebFilter.ATTR_TRACE_ID, "");
    }

    private void assertCanOperateTarget(JwtPrincipal principal, long userId) {
        var target = userMapper.selectById(userId);
        if (target == null || (target.getDeleted() != null && target.getDeleted() != 0)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        var actorRole = principal.role() == null ? "" : principal.role().trim().toUpperCase(Locale.ROOT);
        var targetRole = target.getRole() == null ? "" : target.getRole().trim().toUpperCase(Locale.ROOT);
        if (Role.SUPER_ADMIN.name().equals(targetRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "禁止操作超级管理员账号");
        }
        if (Role.ADMIN.name().equals(actorRole) && !Role.USER.name().equals(targetRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "普通管理员仅可操作普通用户");
        }
        if (!Role.USER.name().equals(targetRole) && !Role.ADMIN.name().equals(targetRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private JwtPrincipal requirePrincipal(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        var role = principal.role() == null ? "" : principal.role().trim().toUpperCase(Locale.ROOT);
        if (!Role.ADMIN.name().equals(role) && !Role.SUPER_ADMIN.name().equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return principal;
    }
}
