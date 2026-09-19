package io.arknights.dateorfriends.modules.user.profile.controller;

import io.arknights.dateorfriends.modules.user.profile.service.UserProfileService;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/user/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    public record UpdateProfileRequest(
            String featuredRole,
            String signature,
            String birthday,
            Boolean birthdayVisible,
            @Size(max = 3) List<String> tags,
            String avatarCharId,
            String avatarCharName,
            String qq,
            String wechat,
            String email
    ) {
    }

    @GetMapping
    public Mono<ApiResponse<UserProfileService.ProfileResponse>> getMyProfile(ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return userProfileService.getProfile(principal.userId(), principal.userId()).map(ApiResponse::ok);
    }

    @GetMapping("/{userId}")
    public Mono<ApiResponse<UserProfileService.ProfileResponse>> getProfile(@PathVariable("userId") long userId, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return userProfileService.getProfile(principal.userId(), userId).map(ApiResponse::ok);
    }

    @PutMapping
    public Mono<ApiResponse<UserProfileService.ProfileResponse>> updateMyProfile(@Valid @RequestBody UpdateProfileRequest req, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        if ("SUPER_ADMIN".equalsIgnoreCase(String.valueOf(principal.role()))) {
            return Mono.error(new BusinessException(ErrorCode.FORBIDDEN, "超级管理员禁止修改个人资料"));
        }
        var ip = IpUtils.resolveClientIp(exchange);
        return userProfileService.updateProfile(principal.userId(), ip, req).map(ApiResponse::ok);
    }
}
