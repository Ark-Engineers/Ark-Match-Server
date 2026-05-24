package io.arknights.dateorfriends.tools.security;

import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.jwt.JwtService;
import io.arknights.dateorfriends.tools.jwt.JwtTokenType;
import io.arknights.dateorfriends.tools.security.token.RedisTokenStore;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-100)
public class AuthWebFilter implements WebFilter {

    public static final String ATTR_PRINCIPAL = "auth.principal";

    private final JwtService jwtService;
    private final RedisTokenStore tokenStore;

    public AuthWebFilter(JwtService jwtService, RedisTokenStore tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var method = exchange.getRequest().getMethod();
        if (method != null && "OPTIONS".equalsIgnoreCase(method.name())) {
            return chain.filter(exchange);
        }

        var rawPath = exchange.getRequest().getPath().value();
        var path = normalizePath(rawPath);
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        var token = resolveBearerToken(exchange);
        if (token == null) {
            return write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }
        JwtPrincipal principal;
        try {
            principal = jwtService.parseAndValidate(token, JwtTokenType.ACCESS);
        } catch (Exception e) {
            return write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }
        return tokenStore.isBlacklisted(principal.jti())
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_REVOKED);
                    }
                    return tokenStore.getTokenVersion(principal.userId())
                            .flatMap(currentVer -> {
                                if (currentVer != principal.tokenVersion()) {
                                    return write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_REVOKED);
                                }
                                if (!checkPermission(path, principal)) {
                                    return write(exchange, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
                                }
                                exchange.getAttributes().put(ATTR_PRINCIPAL, principal);
                                exchange.getAttributes().put("auth.token", token);
                                return chain.filter(exchange);
                            });
                });
    }

    private boolean isPublicPath(String path) {
        return "/auth/login".equals(path)
                || "/auth/register".equals(path)
                || "/auth/email/available".equals(path)
                || "/auth/captcha/new".equals(path)
                || "/auth/register/email-code/send".equals(path)
                || "/auth/login/email-code/send".equals(path)
                || "/auth/login/email-code/verify".equals(path)
                || "/auth/dev/email-code/test".equals(path)
                || "/auth/dev/captcha/check".equals(path)
                || "/auth/dev/redis/info".equals(path)
                || "/auth/refresh".equals(path)
                || "/appeal/ban/submit".equals(path)
                || path.startsWith("/actuator");
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        var p = path;
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.startsWith("/api/")) {
            p = p.substring("/api".length());
        }
        return p;
    }

    private String resolveBearerToken(ServerWebExchange exchange) {
        var auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null) {
            return null;
        }
        if (!auth.startsWith("Bearer ")) {
            return null;
        }
        return auth.substring("Bearer ".length()).trim();
    }

    private boolean checkPermission(String path, JwtPrincipal principal) {
        if (path.startsWith("/admin")) {
            var role = principal.role();
            return Role.ADMIN.name().equals(role) || Role.SUPER_ADMIN.name().equals(role);
        }
        return true;
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, ErrorCode errorCode) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var json = "{\"code\":" + errorCode.code() + ",\"message\":\"" + errorCode.defaultMessage() + "\",\"data\":null}";
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
