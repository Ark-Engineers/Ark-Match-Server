package io.arknights.dateorfriends.tools.security.ban;

import io.arknights.dateorfriends.tools.web.IpUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-110)
public class IpBanWebFilter implements WebFilter {
    private final BanService banService;

    public IpBanWebFilter(BanService banService) {
        this.banService = banService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        var ip = IpUtils.resolveClientIp(exchange);
        return banService.checkIpAllowed(ip).then(chain.filter(exchange));
    }
}

