package io.arknights.dateorfriends.tools.security.ban;

import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.BusinessException;
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
                .onErrorResume(BusinessException.class, e -> {
                    if (e.getErrorCode() != ErrorCode.ACCOUNT_BANNED) return Mono.error(e);
                    return write(exchange, HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_BANNED, e.getMessage(), e.getData());
                });
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, ErrorCode errorCode, String message, Object data) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var safeMessage = escapeJson(message == null ? "" : message);
        String dataJson = "null";
        if (data instanceof BanBlockInfo info) {
            var reason = info.reason() == null ? "null" : "\"" + escapeJson(info.reason()) + "\"";
            var effectiveAt = info.effectiveAt() == null ? "null" : "\"" + escapeJson(info.effectiveAt().toString()) + "\"";
            var expiresAt = info.expiresAt() == null ? "null" : "\"" + escapeJson(info.expiresAt().toString()) + "\"";
            var remainingSeconds = info.remainingSeconds() == null ? "null" : String.valueOf(info.remainingSeconds());
            var remainingDays = info.remainingDays() == null ? "null" : String.valueOf(info.remainingDays());
            dataJson = "{"
                    + "\"reason\":" + reason + ","
                    + "\"effectiveAt\":" + effectiveAt + ","
                    + "\"expiresAt\":" + expiresAt + ","
                    + "\"remainingSeconds\":" + remainingSeconds + ","
                    + "\"remainingDays\":" + remainingDays
                    + "}";
        }
        var json = "{\"code\":" + errorCode.code() + ",\"message\":\"" + safeMessage + "\",\"data\":" + dataJson + "}";
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
