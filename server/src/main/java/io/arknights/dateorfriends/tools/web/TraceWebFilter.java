package io.arknights.dateorfriends.tools.web;

import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 龙门币接口请求溯源：为 /user/lmd、/admin/lmd 请求生成 traceId，
 * 挂到请求属性并回写响应头 X-Trace-Id，供流水/领取记录落库对账。
 */
@Component
@Order(-95)
public class TraceWebFilter implements WebFilter {

    public static final String ATTR_TRACE_ID = "trace.id";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    private static final String[] TRACE_PREFIXES = {"/user/lmd", "/admin/lmd"};

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().value();
        boolean traced = false;
        for (var prefix : TRACE_PREFIXES) {
            if (path.startsWith(prefix)) {
                traced = true;
                break;
            }
        }
        if (!traced) {
            return chain.filter(exchange);
        }
        var traceId = UUID.randomUUID().toString().replace("-", "");
        exchange.getAttributes().put(ATTR_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);
        return chain.filter(exchange);
    }
}
