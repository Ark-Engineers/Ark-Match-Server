package io.arknights.dateorfriends.modules.admin.permission.controller;

import io.arknights.dateorfriends.modules.admin.auth.service.TokenAdminService;
import io.arknights.dateorfriends.modules.admin.permission.mapper.AdminRoleOperationLogDO;
import io.arknights.dateorfriends.modules.admin.permission.mapper.AdminRoleOperationLogMapper;
import io.arknights.dateorfriends.modules.user.auth.mapper.UserDO;
import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.security.Role;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/admin/permission")
public class AdminPermissionController {
    private final UserMapper userMapper;
    private final TokenAdminService tokenAdminService;
    private final AdminRoleOperationLogMapper adminRoleOperationLogMapper;

    public AdminPermissionController(UserMapper userMapper, TokenAdminService tokenAdminService, AdminRoleOperationLogMapper adminRoleOperationLogMapper) {
        this.userMapper = userMapper;
        this.tokenAdminService = tokenAdminService;
        this.adminRoleOperationLogMapper = adminRoleOperationLogMapper;
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public record UserItem(
            long userId,
            String account,
            String nickname,
            String email,
            String role,
            String status,
            LocalDateTime createdAt
    ) {
    }

    public record GrantAdminRequest(@Min(1) long userId, boolean confirm) {
    }

    public record RevokeAdminRequest(@Min(1) long userId, boolean confirm) {
    }

    @GetMapping("/users")
    public Mono<ApiResponse<PageResponse<UserItem>>> listUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", defaultValue = "USER") String role,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertSuperAdmin(principal);

        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;

        var safeRole = role == null ? "USER" : role.trim().toUpperCase();
        var safeKeyword = keyword == null ? null : keyword.trim();

        return Mono.fromCallable(() -> {
                    var total = userMapper.countByRoleAndKeyword(safeRole, safeKeyword);
                    var list = userMapper.selectListByRoleAndKeyword(safeRole, safeKeyword, safeSize, offset);
                    var items = list.stream()
                            .map(u -> new UserItem(u.getId(), u.getAccount(), u.getNickname(), u.getEmail(), u.getRole(), u.getStatus(), u.getCreatedAt()))
                            .collect(Collectors.toList());
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @PostMapping("/grant-admin")
    public Mono<ApiResponse<Void>> grantAdmin(@Valid @RequestBody GrantAdminRequest request, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertSuperAdmin(principal);
        if (!request.confirm()) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "需要二次确认"));
        }
        if (principal.userId() == request.userId()) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "禁止修改自身权限"));
        }

        return Mono.fromCallable(() -> userMapper.selectById(request.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    var fromRole = normalizeRole(user.getRole());
                    if (Role.SUPER_ADMIN.name().equals(fromRole) || Role.ADMIN.name().equals(fromRole)) {
                        return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "该用户已是管理员"));
                    }
                    return Mono.fromCallable(() -> userMapper.updateRole(user.getId(), Role.ADMIN.name()))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(rows -> {
                                if (rows <= 0) return Mono.error(new BusinessException(ErrorCode.INTERNAL_ERROR));
                                return insertLog(principal, user, "GRANT_ADMIN", fromRole, Role.ADMIN.name())
                                        .then(tokenAdminService.revokeAll(user.getId()))
                                        .thenReturn(ApiResponse.ok(null));
                            });
                });
    }

    @PostMapping("/revoke-admin")
    public Mono<ApiResponse<Void>> revokeAdmin(@Valid @RequestBody RevokeAdminRequest request, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        assertSuperAdmin(principal);
        if (!request.confirm()) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "需要二次确认"));
        }
        if (principal.userId() == request.userId()) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "禁止修改自身权限"));
        }

        return Mono.fromCallable(() -> userMapper.selectById(request.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    var fromRole = normalizeRole(user.getRole());
                    if (Role.SUPER_ADMIN.name().equals(fromRole)) {
                        return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "禁止撤销超级管理员权限"));
                    }
                    if (Role.USER.name().equals(fromRole)) {
                        return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "该用户当前不是管理员"));
                    }
                    return Mono.fromCallable(() -> userMapper.updateRole(user.getId(), Role.USER.name()))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(rows -> {
                                if (rows <= 0) return Mono.error(new BusinessException(ErrorCode.INTERNAL_ERROR));
                                return insertLog(principal, user, "REVOKE_ADMIN", fromRole, Role.USER.name())
                                        .then(tokenAdminService.revokeAll(user.getId()))
                                        .thenReturn(ApiResponse.ok(null));
                            });
                });
    }

    @GetMapping("/logs")
    public Mono<ApiResponse<PageResponse<AdminRoleOperationLogDO>>> listLogs(
            @RequestParam(value = "targetUserId", required = false) Long targetUserId,
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "actionType", required = false) String actionType,
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

        LocalDateTime from = parseDateTime(fromTime);
        LocalDateTime to = parseDateTime(toTime);

        return Mono.fromCallable(() -> {
                    var total = adminRoleOperationLogMapper.count(targetUserId, actorId, actionType, from, to);
                    var items = adminRoleOperationLogMapper.selectList(targetUserId, actorId, actionType, from, to, safeSize, offset);
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    private static void assertSuperAdmin(JwtPrincipal principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (!Role.SUPER_ADMIN.name().equals(principal.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅超级管理员可操作");
        }
    }

    private static String normalizeRole(String role) {
        return String.valueOf(role == null ? "" : role).trim().toUpperCase();
    }

    private Mono<Void> insertLog(JwtPrincipal actor, UserDO target, String actionType, String fromRole, String toRole) {
        return Mono.fromRunnable(() -> {
                    var log = new AdminRoleOperationLogDO();
                    log.setActorId(actor.userId());
                    log.setActorRole(actor.role());
                    log.setTargetUserId(target.getId());
                    log.setActionType(actionType);
                    log.setFromRole(fromRole);
                    log.setToRole(toRole);
                    log.setCreatedAt(LocalDateTime.now());
                    adminRoleOperationLogMapper.insert(log);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
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
}
