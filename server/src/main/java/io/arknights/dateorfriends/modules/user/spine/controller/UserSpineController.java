package io.arknights.dateorfriends.modules.user.spine.controller;

import io.arknights.dateorfriends.modules.user.spine.service.UserSpineService;
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
@RequestMapping("/user/spine")
public class UserSpineController {

    private final UserSpineService userSpineService;

    public UserSpineController(UserSpineService userSpineService) {
        this.userSpineService = userSpineService;
    }

    @GetMapping("/list")
    public Mono<ApiResponse<List<UserSpineService.SpineOption>>> list(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return userSpineService.listOptions().map(ApiResponse::ok);
    }
}

