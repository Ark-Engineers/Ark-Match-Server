package io.arknights.dateorfriends.modules.admin.user_manage.controller;

import io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordMapper;
import io.arknights.dateorfriends.modules.admin.permission.mapper.AdminRoleOperationLogDO;
import io.arknights.dateorfriends.modules.admin.permission.mapper.AdminRoleOperationLogMapper;
import io.arknights.dateorfriends.modules.admin.user_manage.mapper.UserManageOperationLogDO;
import io.arknights.dateorfriends.modules.admin.user_manage.mapper.UserManageOperationLogMapper;
import io.arknights.dateorfriends.modules.admin.auth.service.TokenAdminService;
import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import io.arknights.dateorfriends.modules.user.notification.service.SiteNotificationService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.security.Role;
import io.arknights.dateorfriends.tools.security.ban.BanService;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/admin/user-manage")
public class UserManageAdminController {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TokenAdminService tokenAdminService;
    private final BanService banService;
    private final BanRecordMapper banRecordMapper;
    private final AdminRoleOperationLogMapper adminRoleOperationLogMapper;
    private final UserManageOperationLogMapper userManageOperationLogMapper;
    private final SiteNotificationService notificationService;

    public UserManageAdminController(
            UserMapper userMapper,
            BCryptPasswordEncoder passwordEncoder,
            TokenAdminService tokenAdminService,
            BanService banService,
            BanRecordMapper banRecordMapper,
            AdminRoleOperationLogMapper adminRoleOperationLogMapper,
            UserManageOperationLogMapper userManageOperationLogMapper,
            SiteNotificationService notificationService
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenAdminService = tokenAdminService;
        this.banService = banService;
        this.banRecordMapper = banRecordMapper;
        this.adminRoleOperationLogMapper = adminRoleOperationLogMapper;
        this.userManageOperationLogMapper = userManageOperationLogMapper;
        this.notificationService = notificationService;
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public record UserItem(
            long id,
            String account,
            String email,
            String role,
            String nickname,
            String avatarUrl,
            String status,
            LocalDateTime emailVerifiedAt,
            LocalDateTime lastLoginAt,
            String lastLoginIp,
            Integer loginFailCount,
            LocalDateTime lockedUntil,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Integer deleted,
            LocalDateTime deletedAt
    ) {
    }

    public record UpdateProfileRequest(@Min(1) long userId, String account, String nickname, String reason, boolean confirm) {
    }

    public record ResetPasswordRequest(@Min(1) long userId, String reason, boolean confirm) {
    }

    public record ResetPasswordResponse(String tempPassword) {
    }

    public record DeactivateRequest(@Min(1) long userId, String reason, boolean confirm) {
    }

    public record GrantAdminRequest(@Min(1) long userId, String reason, boolean confirm) {
    }

    public record RevokeAdminRequest(@Min(1) long userId, String reason, boolean confirm) {
    }

    public record BanUserRequest(@Min(1) long userId, String reason, Long durationSeconds) {
    }

    public record UnbanUserRequest(@Min(1) long userId, String reason, boolean confirm) {
    }

    public record AdminToggleRequest(@Min(1) long userId, String reason, boolean confirm) {
    }

    @GetMapping("/users")
    public Mono<ApiResponse<PageResponse<UserItem>>> listUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "account", required = false) String account,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "createdFrom", required = false) String createdFrom,
            @RequestParam(value = "createdTo", required = false) String createdTo,
            @RequestParam(value = "includeDeleted", defaultValue = "0") int includeDeleted,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);

        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;

        var safeAccount = normalizeLike(account);
        var safeNickname = normalizeLike(nickname);
        var safeRole = normalizeEnum(role);
        var safeStatus = normalizeEnum(status);
        var safeKeyword = normalizeLike(keyword);

        var from = parseDateTime(createdFrom);
        var to = parseDateTime(createdTo);
        var safeIncludeDeleted = includeDeleted == 1 ? 1 : 0;

        return Mono.fromCallable(() -> {
                    var total = userMapper.countForAdmin(safeAccount, safeNickname, safeRole, safeStatus, safeKeyword, from, to, safeIncludeDeleted);
                    var list = userMapper.selectListForAdmin(safeAccount, safeNickname, safeRole, safeStatus, safeKeyword, from, to, safeIncludeDeleted, safeSize, offset);
                    var items = list.stream()
                            .map(u -> new UserItem(
                                    u.getId() == null ? 0 : u.getId(),
                                    u.getAccount(),
                                    u.getEmail(),
                                    u.getRole(),
                                    u.getNickname(),
                                    u.getAvatarUrl(),
                                    u.getStatus(),
                                    u.getEmailVerifiedAt(),
                                    u.getLastLoginAt(),
                                    u.getLastLoginIp(),
                                    u.getLoginFailCount(),
                                    u.getLockedUntil(),
                                    u.getCreatedAt(),
                                    u.getUpdatedAt(),
                                    u.getDeleted(),
                                    u.getDeletedAt()
                            ))
                            .toList();
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @GetMapping("/users/{id}")
    public Mono<ApiResponse<UserItem>> userDetail(@PathVariable("id") long id, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);
        if (id <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));

        return Mono.fromCallable(() -> userMapper.selectById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(u -> {
                    if (u == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    return Mono.just(ApiResponse.ok(new UserItem(
                            u.getId() == null ? 0 : u.getId(),
                            u.getAccount(),
                            u.getEmail(),
                            u.getRole(),
                            u.getNickname(),
                            u.getAvatarUrl(),
                            u.getStatus(),
                            u.getEmailVerifiedAt(),
                            u.getLastLoginAt(),
                            u.getLastLoginIp(),
                            u.getLoginFailCount(),
                            u.getLockedUntil(),
                            u.getCreatedAt(),
                            u.getUpdatedAt(),
                            u.getDeleted(),
                            u.getDeletedAt()
                    )));
                });
    }

    @PostMapping("/update-profile")
    public Mono<ApiResponse<Void>> updateProfile(@Valid @RequestBody UpdateProfileRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);
        if (!req.confirm()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "需要二次确认"));

        var safeAccount = normalizeNullable(req.account());
        var safeNickname = normalizeNullable(req.nickname());
        if (safeAccount == null && safeNickname == null) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        }

        var ip = IpUtils.resolveClientIp(exchange);
        return Mono.fromCallable(() -> userMapper.selectById(req.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    assertCanOperateTarget(principal, user.getRole());

                    if (safeAccount != null && !safeAccount.equals(user.getAccount())) {
                        var count = userMapper.countByAccountAll(safeAccount);
                        if (count > 0) throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
                    }

                    var beforeAccount = user.getAccount();
                    var beforeNickname = user.getNickname();

                    return Mono.fromCallable(() -> userMapper.updateProfile(user.getId(), safeAccount, safeNickname))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(rows -> {
                                if (rows <= 0) return Mono.error(new BusinessException(ErrorCode.OP_FAILED));

                                var diffJson = buildProfileDiffJson(beforeAccount, safeAccount, beforeNickname, safeNickname);
                                var detail = "UPDATE_PROFILE";
                                return insertUserManageLog(principal, user.getId(), "UPDATE_PROFILE", ip, detail, diffJson)
                                        .then(notificationService.sendToUser(principal.userId(), user.getId(), "ACCOUNT", "账号信息已更新",
                                                buildProfileNotifyContent(beforeAccount, safeAccount, beforeNickname, safeNickname, req.reason()),
                                                "NORMAL", null, null))
                                        .thenReturn(ApiResponse.ok(null));
                            });
                });
    }

    @PostMapping("/reset-password")
    public Mono<ApiResponse<ResetPasswordResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);
        if (!req.confirm()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "需要二次确认"));
        if (principal.userId() == req.userId()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "禁止修改自身密码"));

        var ip = IpUtils.resolveClientIp(exchange);
        return Mono.fromCallable(() -> userMapper.selectById(req.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    assertCanOperateTarget(principal, user.getRole());

                    var tempPassword = generateTempPassword();
                    var passwordHash = passwordEncoder.encode(tempPassword);
                    return Mono.fromCallable(() -> userMapper.updatePasswordHash(user.getId(), passwordHash))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(rows -> {
                                if (rows <= 0) return Mono.error(new BusinessException(ErrorCode.OP_FAILED));
                                return tokenAdminService.revokeAll(user.getId())
                                        .then(insertUserManageLog(principal, user.getId(), "RESET_PASSWORD", ip, "RESET_PASSWORD", null))
                                        .then(notificationService.sendToUser(principal.userId(), user.getId(), "SECURITY", "登录密码已重置",
                                                buildResetPasswordNotifyContent(tempPassword, req.reason()),
                                                "IMPORTANT", null, null))
                                        .thenReturn(ApiResponse.ok(new ResetPasswordResponse(tempPassword)));
                            });
                });
    }

    @PostMapping("/deactivate")
    public Mono<ApiResponse<Void>> deactivate(@Valid @RequestBody DeactivateRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);
        if (!req.confirm()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "需要二次确认"));
        if (principal.userId() == req.userId()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "禁止注销自身账号"));

        var ip = IpUtils.resolveClientIp(exchange);
        return Mono.fromCallable(() -> userMapper.selectById(req.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    assertCanOperateTarget(principal, user.getRole());
                    var now = LocalDateTime.now();
                    return Mono.fromCallable(() -> userMapper.softDelete(user.getId(), now))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(rows -> {
                                if (rows <= 0) return Mono.error(new BusinessException(ErrorCode.OP_FAILED));
                                return tokenAdminService.revokeAll(user.getId())
                                        .then(insertUserManageLog(principal, user.getId(), "DEACTIVATE", ip, "DEACTIVATE", null))
                                        .then(notificationService.sendToUser(principal.userId(), user.getId(), "ACCOUNT", "账号已被注销",
                                                buildDeactivateNotifyContent(req.reason()),
                                                "IMPORTANT", null, null))
                                        .thenReturn(ApiResponse.ok(null));
                            });
                });
    }

    @PostMapping("/grant-admin")
    public Mono<ApiResponse<Void>> grantAdmin(@Valid @RequestBody GrantAdminRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertSuperAdmin(principal);
        if (!req.confirm()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "需要二次确认"));
        if (principal.userId() == req.userId()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "禁止修改自身权限"));

        var ip = IpUtils.resolveClientIp(exchange);
        return Mono.fromCallable(() -> userMapper.selectById(req.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    var fromRole = normalizeEnum(user.getRole());
                    if (Role.SUPER_ADMIN.name().equals(fromRole) || Role.ADMIN.name().equals(fromRole)) {
                        return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "该用户已是管理员"));
                    }

                    return Mono.fromCallable(() -> userMapper.updateRole(user.getId(), Role.ADMIN.name()))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(rows -> {
                                if (rows <= 0) return Mono.error(new BusinessException(ErrorCode.OP_FAILED));
                                return insertRoleLog(principal, user.getId(), "GRANT_ADMIN", fromRole, Role.ADMIN.name())
                                        .then(tokenAdminService.revokeAll(user.getId()))
                                        .then(insertUserManageLog(principal, user.getId(), "GRANT_ADMIN", ip, "GRANT_ADMIN", buildRoleDiffJson(fromRole, Role.ADMIN.name())))
                                        .then(notificationService.sendToUser(principal.userId(), user.getId(), "ACCOUNT", "权限已变更",
                                                buildRoleNotifyContent("已升级为管理员（ADMIN）", req.reason()),
                                                "IMPORTANT", null, null))
                                        .thenReturn(ApiResponse.ok(null));
                            });
                });
    }

    @PostMapping("/revoke-admin")
    public Mono<ApiResponse<Void>> revokeAdmin(@Valid @RequestBody RevokeAdminRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertSuperAdmin(principal);
        if (!req.confirm()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "需要二次确认"));
        if (principal.userId() == req.userId()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "禁止修改自身权限"));

        var ip = IpUtils.resolveClientIp(exchange);
        return Mono.fromCallable(() -> userMapper.selectById(req.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    var fromRole = normalizeEnum(user.getRole());
                    if (Role.SUPER_ADMIN.name().equals(fromRole)) {
                        return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "禁止撤销超级管理员权限"));
                    }
                    if (Role.USER.name().equals(fromRole)) {
                        return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "该用户当前不是管理员"));
                    }

                    return Mono.fromCallable(() -> userMapper.updateRole(user.getId(), Role.USER.name()))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(rows -> {
                                if (rows <= 0) return Mono.error(new BusinessException(ErrorCode.OP_FAILED));
                                return insertRoleLog(principal, user.getId(), "REVOKE_ADMIN", fromRole, Role.USER.name())
                                        .then(tokenAdminService.revokeAll(user.getId()))
                                        .then(insertUserManageLog(principal, user.getId(), "REVOKE_ADMIN", ip, "REVOKE_ADMIN", buildRoleDiffJson(fromRole, Role.USER.name())))
                                        .then(notificationService.sendToUser(principal.userId(), user.getId(), "ACCOUNT", "权限已变更",
                                                buildRoleNotifyContent("管理员权限已被撤销（USER）", req.reason()),
                                                "IMPORTANT", null, null))
                                        .thenReturn(ApiResponse.ok(null));
                            });
                });
    }

    @PostMapping("/ban")
    public Mono<ApiResponse<Void>> banUser(@Valid @RequestBody BanUserRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);

        var ip = IpUtils.resolveClientIp(exchange);
        return Mono.fromCallable(() -> userMapper.selectById(req.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    assertCanOperateTarget(principal, user.getRole());
                    return banService.assertBanPermission(principal)
                            .then(banService.banUser(principal.userId(), req.userId(), normalizeNullable(req.reason()), req.durationSeconds()))
                            .flatMap(record -> insertUserManageLog(principal, req.userId(), "BAN", ip, "BAN", buildBanDiffJson("BAN", req.reason(), req.durationSeconds()))
                                    .then(notificationService.sendToUser(principal.userId(), req.userId(), "SECURITY", "账号已被封禁",
                                            buildBanNotifyContent(req.reason(), req.durationSeconds()),
                                            "IMPORTANT", null, null))
                                    .thenReturn(ApiResponse.ok(null)));
                });
    }

    @PostMapping("/unban")
    public Mono<ApiResponse<Void>> unbanUser(@Valid @RequestBody UnbanUserRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);
        if (!req.confirm()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "需要二次确认"));

        var ip = IpUtils.resolveClientIp(exchange);
        return Mono.fromCallable(() -> userMapper.selectById(req.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    assertCanOperateTarget(principal, user.getRole());
                    return banService.assertBanPermission(principal)
                            .then(Mono.fromCallable(() -> banRecordMapper.selectListForUser(req.userId(), "ACTIVE", 5000, 0))
                                    .subscribeOn(Schedulers.boundedElastic()))
                            .flatMapMany(Flux::fromIterable)
                            .concatMap(r -> banService.unbanByRecordId(principal.userId(), r.getId()))
                            .then(insertUserManageLog(principal, req.userId(), "UNBAN", ip, "UNBAN", buildUnbanDiffJson(req.reason())))
                            .then(notificationService.sendToUser(principal.userId(), req.userId(), "SECURITY", "账号已被解封",
                                    buildUnbanNotifyContent(req.reason()),
                                    "IMPORTANT", null, null))
                            .thenReturn(ApiResponse.ok(null));
                });
    }

    @PostMapping("/admin/disable")
    public Mono<ApiResponse<Void>> disableAdmin(@Valid @RequestBody AdminToggleRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertSuperAdmin(principal);
        if (!req.confirm()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "需要二次确认"));
        if (principal.userId() == req.userId()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "禁止禁用自身账号"));

        var ip = IpUtils.resolveClientIp(exchange);
        return Mono.fromCallable(() -> userMapper.selectById(req.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    var role = normalizeEnum(user.getRole());
                    if (!Role.ADMIN.name().equals(role)) return Mono.error(new BusinessException(ErrorCode.FORBIDDEN, "仅允许禁用管理员账号"));
                    if ("SUSPENDED".equalsIgnoreCase(String.valueOf(user.getStatus()))) return Mono.just(ApiResponse.ok(null));

                    return Mono.fromCallable(() -> userMapper.updateStatus(user.getId(), "SUSPENDED"))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(rows -> {
                                if (rows <= 0) return Mono.error(new BusinessException(ErrorCode.OP_FAILED));
                                return tokenAdminService.revokeAll(user.getId())
                                        .then(insertUserManageLog(principal, user.getId(), "DISABLE_ADMIN", ip, "DISABLE_ADMIN", "{\"status\":{\"from\":\"" + user.getStatus() + "\",\"to\":\"SUSPENDED\"}}"))
                                        .then(notificationService.sendToUser(principal.userId(), user.getId(), "SECURITY", "管理员账号已被禁用",
                                                buildRoleNotifyContent("你的管理员账号已被禁用（SUSPENDED）", req.reason()),
                                                "IMPORTANT", null, null))
                                        .thenReturn(ApiResponse.ok(null));
                            });
                });
    }

    @PostMapping("/admin/enable")
    public Mono<ApiResponse<Void>> enableAdmin(@Valid @RequestBody AdminToggleRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertSuperAdmin(principal);
        if (!req.confirm()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "需要二次确认"));

        var ip = IpUtils.resolveClientIp(exchange);
        return Mono.fromCallable(() -> userMapper.selectById(req.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    var role = normalizeEnum(user.getRole());
                    if (!Role.ADMIN.name().equals(role)) return Mono.error(new BusinessException(ErrorCode.FORBIDDEN, "仅允许启用管理员账号"));
                    if (!"SUSPENDED".equalsIgnoreCase(String.valueOf(user.getStatus()))) return Mono.just(ApiResponse.ok(null));

                    return Mono.fromCallable(() -> userMapper.updateStatus(user.getId(), "NORMAL"))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(rows -> {
                                if (rows <= 0) return Mono.error(new BusinessException(ErrorCode.OP_FAILED));
                                return tokenAdminService.revokeAll(user.getId())
                                        .then(insertUserManageLog(principal, user.getId(), "ENABLE_ADMIN", ip, "ENABLE_ADMIN", "{\"status\":{\"from\":\"" + user.getStatus() + "\",\"to\":\"NORMAL\"}}"))
                                        .then(notificationService.sendToUser(principal.userId(), user.getId(), "SECURITY", "管理员账号已启用",
                                                buildRoleNotifyContent("你的管理员账号已被启用（NORMAL）", req.reason()),
                                                "IMPORTANT", null, null))
                                        .thenReturn(ApiResponse.ok(null));
                            });
                });
    }

    @GetMapping("/logs")
    public Mono<ApiResponse<PageResponse<UserManageOperationLogDO>>> listLogs(
            @RequestParam(value = "targetUserId", required = false) Long targetUserId,
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "fromTime", required = false) String fromTime,
            @RequestParam(value = "toTime", required = false) String toTime,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertSuperAdmin(principal);

        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;

        var safeActionType = normalizeEnum(actionType);
        var safeKeyword = normalizeLike(keyword);
        var from = parseDateTime(fromTime);
        var to = parseDateTime(toTime);

        return Mono.fromCallable(() -> {
                    var total = userManageOperationLogMapper.count(targetUserId, actorId, safeActionType, safeKeyword, from, to);
                    var items = userManageOperationLogMapper.selectList(targetUserId, actorId, safeActionType, safeKeyword, from, to, safeSize, offset);
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @GetMapping("/logs/export")
    public Mono<ResponseEntity<byte[]>> exportLogs(
            @RequestParam(value = "targetUserId", required = false) Long targetUserId,
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "fromTime", required = false) String fromTime,
            @RequestParam(value = "toTime", required = false) String toTime,
            @RequestParam(value = "ids", required = false) String ids,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertSuperAdmin(principal);

        var safeActionType = normalizeEnum(actionType);
        var safeKeyword = normalizeLike(keyword);
        var from = parseDateTime(fromTime);
        var to = parseDateTime(toTime);

        return Mono.fromCallable(() -> {
                    var idList = parseIds(ids);
                    if (idList != null && !idList.isEmpty()) {
                        return userManageOperationLogMapper.selectListByIds(idList);
                    }
                    return userManageOperationLogMapper.selectList(targetUserId, actorId, safeActionType, safeKeyword, from, to, 5000, 0);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toCsv)
                .map(csv -> csv.getBytes(StandardCharsets.UTF_8))
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=user-manage-logs.csv")
                        .contentType(new MediaType("text", "csv"))
                        .body(bytes));
    }

    @GetMapping("/users/{id}/bans")
    public Mono<ApiResponse<PageResponse<io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordDO>>> listUserBans(
            @PathVariable("id") long id,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertAdmin(principal);
        if (id <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));

        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;
        var safeStatus = normalizeEnum(status);

        return Mono.fromCallable(() -> {
                    var total = banRecordMapper.countForUser(id, safeStatus);
                    var items = banRecordMapper.selectListForUser(id, safeStatus, safeSize, offset);
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    private static void assertAdmin(JwtPrincipal principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var role = String.valueOf(principal.role() == null ? "" : principal.role()).trim().toUpperCase();
        if (!Role.ADMIN.name().equals(role) && !Role.SUPER_ADMIN.name().equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private static void assertSuperAdmin(JwtPrincipal principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (!Role.SUPER_ADMIN.name().equals(principal.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅超级管理员可访问");
        }
    }

    private static void assertCanOperateTarget(JwtPrincipal principal, String targetRoleRaw) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var actorRole = String.valueOf(principal.role() == null ? "" : principal.role()).trim().toUpperCase();
        var targetRole = normalizeEnum(targetRoleRaw);
        if (Role.SUPER_ADMIN.name().equals(targetRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "禁止操作超级管理员账号");
        }
        if (Role.ADMIN.name().equals(actorRole)) {
            if (!Role.USER.name().equals(targetRole)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "普通管理员仅可操作普通用户");
            }
        }
    }

    private static String normalizeNullable(String v) {
        if (v == null) return null;
        var s = v.trim();
        return s.isBlank() ? null : s;
    }

    private static String normalizeLike(String v) {
        if (v == null) return null;
        var s = v.trim();
        return s.isBlank() ? null : s;
    }

    private static String normalizeEnum(String v) {
        if (v == null) return null;
        var s = v.trim();
        return s.isBlank() ? null : s.toUpperCase();
    }

    private static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var v = raw.trim();
        try {
            if (v.length() == 16) {
                return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            }
            return LocalDateTime.parse(v);
        } catch (Exception e) {
            return null;
        }
    }

    private Mono<Void> insertRoleLog(JwtPrincipal actor, long targetUserId, String actionType, String fromRole, String toRole) {
        return Mono.fromRunnable(() -> {
                    var log = new AdminRoleOperationLogDO();
                    log.setActorId(actor.userId());
                    log.setActorRole(actor.role());
                    log.setTargetUserId(targetUserId);
                    log.setActionType(actionType);
                    log.setFromRole(fromRole);
                    log.setToRole(toRole);
                    log.setCreatedAt(LocalDateTime.now());
                    adminRoleOperationLogMapper.insert(log);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Void> insertUserManageLog(JwtPrincipal actor, long targetUserId, String actionType, String ip, String detail, String diffJson) {
        return Mono.fromRunnable(() -> {
                    var log = new UserManageOperationLogDO();
                    log.setActorId(actor.userId());
                    log.setActorRole(actor.role());
                    log.setTargetUserId(targetUserId);
                    log.setActionType(actionType);
                    log.setIp(ip);
                    log.setDetail(detail);
                    log.setDiffJson(diffJson);
                    log.setCreatedAt(LocalDateTime.now());
                    userManageOperationLogMapper.insert(log);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private static final SecureRandom RAND = new SecureRandom();
    private static final char[] TEMP_PW_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();

    private static String generateTempPassword() {
        var len = 10;
        var buf = new char[len];
        for (int i = 0; i < len; i++) {
            buf[i] = TEMP_PW_CHARS[RAND.nextInt(TEMP_PW_CHARS.length)];
        }
        return new String(buf);
    }

    private static String jsonString(String v) {
        if (v == null) return "null";
        var s = v.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + s + "\"";
    }

    private static String buildProfileDiffJson(String beforeAccount, String afterAccount, String beforeNickname, String afterNickname) {
        var sb = new StringBuilder();
        sb.append('{');
        boolean any = false;
        if (afterAccount != null && !afterAccount.equals(beforeAccount)) {
            sb.append("\"account\":{\"from\":").append(jsonString(beforeAccount)).append(",\"to\":").append(jsonString(afterAccount)).append('}');
            any = true;
        }
        if (afterNickname != null && !afterNickname.equals(beforeNickname)) {
            if (any) sb.append(',');
            sb.append("\"nickname\":{\"from\":").append(jsonString(beforeNickname)).append(",\"to\":").append(jsonString(afterNickname)).append('}');
            any = true;
        }
        sb.append('}');
        return any ? sb.toString() : null;
    }

    private static String buildRoleDiffJson(String fromRole, String toRole) {
        return "{\"role\":{\"from\":" + jsonString(fromRole) + ",\"to\":" + jsonString(toRole) + "}}";
    }

    private static String buildBanDiffJson(String action, String reason, Long durationSeconds) {
        return "{\"action\":" + jsonString(action) + ",\"reason\":" + jsonString(reason) + ",\"durationSeconds\":" + (durationSeconds == null ? "null" : durationSeconds) + "}";
    }

    private static String buildUnbanDiffJson(String reason) {
        return "{\"action\":\"UNBAN\",\"reason\":" + jsonString(reason) + "}";
    }

    private static String buildProfileNotifyContent(String beforeAccount, String afterAccount, String beforeNickname, String afterNickname, String reason) {
        var sb = new StringBuilder();
        sb.append("你的账号信息已被超级管理员更新。");
        if (afterAccount != null && !afterAccount.equals(beforeAccount)) {
            sb.append("\n账号：").append(beforeAccount == null ? "" : beforeAccount).append(" -> ").append(afterAccount);
        }
        if (afterNickname != null && !afterNickname.equals(beforeNickname)) {
            sb.append("\n昵称：").append(beforeNickname == null ? "" : beforeNickname).append(" -> ").append(afterNickname);
        }
        if (reason != null && !reason.isBlank()) {
            sb.append("\n原因：").append(reason.trim());
        }
        return sb.toString();
    }

    private static String buildResetPasswordNotifyContent(String tempPassword, String reason) {
        var sb = new StringBuilder();
        sb.append("你的登录密码已被超级管理员重置。");
        sb.append("\n临时密码：").append(tempPassword);
        sb.append("\n请尽快登录后修改密码。");
        if (reason != null && !reason.isBlank()) {
            sb.append("\n原因：").append(reason.trim());
        }
        return sb.toString();
    }

    private static String buildDeactivateNotifyContent(String reason) {
        var sb = new StringBuilder();
        sb.append("你的账号已被超级管理员执行注销处理。");
        if (reason != null && !reason.isBlank()) {
            sb.append("\n原因：").append(reason.trim());
        }
        return sb.toString();
    }

    private static String buildRoleNotifyContent(String msg, String reason) {
        var sb = new StringBuilder();
        sb.append(msg);
        if (reason != null && !reason.isBlank()) {
            sb.append("\n原因：").append(reason.trim());
        }
        return sb.toString();
    }

    private static String buildBanNotifyContent(String reason, Long durationSeconds) {
        var sb = new StringBuilder();
        sb.append("你的账号已被封禁，当前无法访问系统。");
        if (durationSeconds != null && durationSeconds > 0) {
            sb.append("\n封禁时长（秒）：").append(durationSeconds);
        } else {
            sb.append("\n封禁时长：永久");
        }
        if (reason != null && !reason.isBlank()) {
            sb.append("\n原因：").append(reason.trim());
        }
        return sb.toString();
    }

    private static String buildUnbanNotifyContent(String reason) {
        var sb = new StringBuilder();
        sb.append("你的账号已被解封。");
        if (reason != null && !reason.isBlank()) {
            sb.append("\n原因：").append(reason.trim());
        }
        return sb.toString();
    }

    private List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) return null;
        try {
            return java.util.Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(Long::parseLong)
                    .filter(x -> x > 0)
                    .distinct()
                    .toList();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toCsv(List<UserManageOperationLogDO> list) {
        var sb = new StringBuilder();
        sb.append("id,actorId,actorRole,targetUserId,actionType,ip,detail,diffJson,createdAt\n");
        for (var r : list) {
            sb.append(safe(r.getId()))
                    .append(',').append(safe(r.getActorId()))
                    .append(',').append(safe(r.getActorRole()))
                    .append(',').append(safe(r.getTargetUserId()))
                    .append(',').append(safe(r.getActionType()))
                    .append(',').append(safe(r.getIp()))
                    .append(',').append(csvValue(r.getDetail()))
                    .append(',').append(csvValue(r.getDiffJson()))
                    .append(',').append(safe(r.getCreatedAt()))
                    .append('\n');
        }
        return sb.toString();
    }

    private String safe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String csvValue(String v) {
        if (v == null) return "";
        var s = v.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }
}
