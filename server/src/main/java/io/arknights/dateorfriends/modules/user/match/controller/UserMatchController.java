package io.arknights.dateorfriends.modules.user.match.controller;

import io.arknights.dateorfriends.modules.user.match.service.UserMatchService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/user/match")
public class UserMatchController {

    private final UserMatchService matchService;

    public UserMatchController(UserMatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping("/recommendations")
    public Mono<ApiResponse<List<UserMatchService.RecommendationItem>>> recommendations(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var safeLimit = Math.min(50, Math.max(1, limit));
        var safeOffset = Math.max(0, offset);
        return matchService.recommend(principal.userId(), safeLimit, safeOffset).map(ApiResponse::ok);
    }

    @GetMapping("/stats")
    public Mono<ApiResponse<MatchStats>> stats(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return Mono.just(ApiResponse.ok(new MatchStats(0, 0, 0)));
    }

    public record MatchStats(int pendingCount, int confirmedCount, int totalCount) {}
}

