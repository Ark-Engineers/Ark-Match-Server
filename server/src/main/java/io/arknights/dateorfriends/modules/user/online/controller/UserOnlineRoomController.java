package io.arknights.dateorfriends.modules.user.online.controller;

import io.arknights.dateorfriends.modules.user.online.ws.OnlineWebSocketHandlerV2;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/user/online")
public class UserOnlineRoomController {

    private final OnlineWebSocketHandlerV2 onlineWebSocketHandlerV2;

    public UserOnlineRoomController(OnlineWebSocketHandlerV2 onlineWebSocketHandlerV2) {
        this.onlineWebSocketHandlerV2 = onlineWebSocketHandlerV2;
    }

    @GetMapping("/rooms")
    public Mono<ApiResponse<List<OnlineWebSocketHandlerV2.RoomCard>>> rooms(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return Mono.just(ApiResponse.ok(onlineWebSocketHandlerV2.listRooms(principal)));
    }
}

