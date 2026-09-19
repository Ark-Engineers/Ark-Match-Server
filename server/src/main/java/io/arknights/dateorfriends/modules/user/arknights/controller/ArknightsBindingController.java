package io.arknights.dateorfriends.modules.user.arknights.controller;

import io.arknights.dateorfriends.modules.user.arknights.service.ArknightsBindingService;
import io.arknights.dateorfriends.modules.user.arknights.service.ArknightsBindingService.BindRequest;
import io.arknights.dateorfriends.modules.user.arknights.service.ArknightsBindingService.BindingStatus;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController("userArknightsBindingController")
@RequestMapping("/user/arknights")
public class ArknightsBindingController {

    private final ArknightsBindingService arknightsBindingService;

    public ArknightsBindingController(ArknightsBindingService arknightsBindingService) {
        this.arknightsBindingService = arknightsBindingService;
    }

    /**
     * 查询当前用户的明日方舟绑定状态。
     * 权限要求：需要登录。
     * 关键约束：仅返回当前用户的数据；成年状态由未成年状态实时派生。
     */
    @GetMapping("/status")
    public Mono<ApiResponse<BindingStatus>> status(ServerWebExchange exchange) {
        var principal = principal(exchange);
        return arknightsBindingService.getStatus(principal.userId()).map(ApiResponse::ok);
    }

    /**
     * 保存官方授权页回传的明日方舟绑定展示数据。
     * 权限要求：需要登录，超级管理员不可修改个人资料。
     * 关键约束：请求仅允许 basic 与 accountBinding 白名单字段，禁止提交或记录第三方凭证。
     */
    @PostMapping("/bind")
    public Mono<ApiResponse<BindingStatus>> bind(@Valid @RequestBody BindRequest request, ServerWebExchange exchange) {
        var principal = principal(exchange);
        ensureProfileEditable(principal);
        return arknightsBindingService.bind(principal.userId(), request).map(ApiResponse::ok);
    }

    /**
     * 解除当前用户的明日方舟绑定。
     * 权限要求：需要登录，超级管理员不可修改个人资料。
     * 关键约束：解绑后清空全部明日方舟业务字段，且不保留任何第三方凭证。
     */
    @PostMapping("/unbind")
    public Mono<ApiResponse<Void>> unbind(ServerWebExchange exchange) {
        var principal = principal(exchange);
        ensureProfileEditable(principal);
        return arknightsBindingService.unbind(principal.userId()).thenReturn(ApiResponse.ok(null));
    }

    private JwtPrincipal principal(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal;
    }

    private void ensureProfileEditable(JwtPrincipal principal) {
        if ("SUPER_ADMIN".equalsIgnoreCase(String.valueOf(principal.role()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "超级管理员禁止修改个人资料");
        }
    }
}
