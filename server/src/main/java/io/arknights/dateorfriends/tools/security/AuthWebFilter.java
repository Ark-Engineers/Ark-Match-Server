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
        var path = exchange.getRequest().getPath().value();
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
                || "/auth/refresh".equals(path)
                || path.startsWith("/actuator");
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
            return Role.ADMIN.name().equals(principal.role());
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
