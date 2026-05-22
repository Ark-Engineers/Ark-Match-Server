package io.arknights.dateorfriends.tools.security.ban;

import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-90)
public class UserBanWebFilter implements WebFilter {
    private final BanService banService;

    public UserBanWebFilter(BanService banService) {
        this.banService = banService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) {
            return chain.filter(exchange);
        }
        return banService.checkUserAllowed(principal.userId())
                .then(chain.filter(exchange))
                .onErrorResume(io.arknights.dateorfriends.tools.web.BusinessException.class, e -> {
                    if (e.getErrorCode() != ErrorCode.ACCOUNT_BANNED) return Mono.error(e);
                    return write(exchange, HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_BANNED);
                });
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

