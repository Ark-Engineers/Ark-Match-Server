package io.arknights.dateorfriends.modules.admin.online.controller;

import io.arknights.dateorfriends.modules.user.online.ws.OnlineWebSocketHandlerV2;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/online/rooms")
public class AdminOnlineRoomController {

    private final OnlineWebSocketHandlerV2 onlineWebSocketHandlerV2;

    public AdminOnlineRoomController(OnlineWebSocketHandlerV2 onlineWebSocketHandlerV2) {
        this.onlineWebSocketHandlerV2 = onlineWebSocketHandlerV2;
    }

    @PostMapping
    public Mono<ApiResponse<OnlineWebSocketHandlerV2.RoomCard>> create(
            @RequestBody OnlineWebSocketHandlerV2.CreateRoomRequest body,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var role = String.valueOf(principal.role() == null ? "" : principal.role()).toUpperCase();
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) return Mono.error(new BusinessException(ErrorCode.FORBIDDEN));
        return onlineWebSocketHandlerV2.createRoom(principal, body)
                .map(ApiResponse::ok)
                .onErrorResume(IllegalArgumentException.class, e -> Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, e.getMessage())))
                .onErrorResume(IllegalStateException.class, e -> Mono.error(new BusinessException(ErrorCode.OP_FAILED, "房间已存在")));
    }

    @PostMapping("/{roomId}/online")
    public Mono<ApiResponse<Void>> online(@PathVariable("roomId") String roomId, ServerWebExchange exchange) {
        return setOnline(roomId, true, exchange);
    }

    @PostMapping("/{roomId}/offline")
    public Mono<ApiResponse<Void>> offline(@PathVariable("roomId") String roomId, ServerWebExchange exchange) {
        return setOnline(roomId, false, exchange);
    }

    private Mono<ApiResponse<Void>> setOnline(String roomId, boolean online, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var role = String.valueOf(principal.role() == null ? "" : principal.role()).toUpperCase();
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) return Mono.error(new BusinessException(ErrorCode.FORBIDDEN));
        return onlineWebSocketHandlerV2.setRoomOnline(principal, roomId, online)
                .thenReturn(ApiResponse.<Void>ok(null))
                .onErrorResume(IllegalArgumentException.class, e -> Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, e.getMessage())));
    }
}
