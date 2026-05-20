package io.arknights.dateorfriends.modules.user.auth.controller;

import io.arknights.dateorfriends.modules.user.auth.service.AuthService;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController("authController")
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 统一登录入口（管理员/普通用户共用）。
     * 成功后签发 Access Token（2小时）与 Refresh Token（15天）。
     */
    @PostMapping("/login")
    public Mono<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request, ServerWebExchange exchange) {
        var ip = resolveIp(exchange);
        return authService.login(request.account(), request.password(), ip).map(ApiResponse::ok);
    }

    /**
     * 刷新令牌入口（仅 Refresh Token 可用）。
     * 每次刷新都会签发新的 Access/Refresh，并作废旧 Refresh（Redis 立即失效 + 黑名单）。
     */
    @PostMapping("/refresh")
    public Mono<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken()).map(ApiResponse::ok);
    }

    /**
     * 登出（主动吊销当前 Access Token）。
     * 可选携带 refreshToken 以同时吊销该 Refresh Token。
     */
    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout(@RequestBody(required = false) LogoutRequest request, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) {
            return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        }
        var accessToken = exchange.<String>getAttribute("auth.token");
        if (accessToken == null) {
            accessToken = resolveBearerToken(exchange);
        }
        var refreshToken = request == null ? null : request.refreshToken();
        return authService.logout(principal, accessToken, refreshToken).thenReturn(ApiResponse.ok(null));
    }

    /**
     * 登出全部（吊销该用户全部端的 Access/Refresh）。
     * 通过 Redis 令牌版本号机制，使历史令牌即时失效。
     */
    @PostMapping("/logout-all")
    public Mono<ApiResponse<Void>> logoutAll(ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) {
            return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        }
        return authService.logoutAll(principal.userId()).thenReturn(ApiResponse.ok(null));
    }

    private String resolveIp(ServerWebExchange exchange) {
        var xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            var idx = xff.indexOf(',');
            return idx >= 0 ? xff.substring(0, idx).trim() : xff.trim();
        }
        var addr = exchange.getRequest().getRemoteAddress();
        if (addr == null || addr.getAddress() == null) {
            return "unknown";
        }
        return addr.getAddress().getHostAddress();
    }

    private String resolveBearerToken(ServerWebExchange exchange) {
        var auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return auth.substring("Bearer ".length()).trim();
    }
}
