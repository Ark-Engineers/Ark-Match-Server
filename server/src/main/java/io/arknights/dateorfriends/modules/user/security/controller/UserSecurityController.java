package io.arknights.dateorfriends.modules.user.security.controller;

import io.arknights.dateorfriends.modules.user.security.service.UserSecurityService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/user/security")
public class UserSecurityController {

    private final UserSecurityService userSecurityService;

    public UserSecurityController(UserSecurityService userSecurityService) {
        this.userSecurityService = userSecurityService;
    }

    public record UpdateNicknameRequest(@NotBlank @Size(max = 64) String nickname) {
    }

    @PostMapping("/nickname")
    public Mono<ApiResponse<Void>> updateNickname(@Valid @RequestBody UpdateNicknameRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = IpUtils.resolveClientIp(exchange);
        return userSecurityService.updateNickname(principal, req.nickname(), ip).thenReturn(ApiResponse.ok(null));
    }

    @PostMapping("/password/email-code/send")
    public Mono<ApiResponse<Void>> sendChangePasswordCode(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = IpUtils.resolveClientIp(exchange);
        return userSecurityService.sendChangePasswordCode(principal, ip).thenReturn(ApiResponse.ok(null));
    }

    public record ChangePasswordRequest(@NotBlank @Size(min = 6, max = 64) String password, @NotBlank @Size(min = 6, max = 6) String emailCode) {
    }

    @PostMapping("/password")
    public Mono<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = IpUtils.resolveClientIp(exchange);
        return userSecurityService.changePassword(principal, req.password(), req.emailCode(), ip).thenReturn(ApiResponse.ok(null));
    }

    @PostMapping("/email/email-code/send")
    public Mono<ApiResponse<Void>> sendChangeEmailCode(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = IpUtils.resolveClientIp(exchange);
        return userSecurityService.sendChangeEmailCode(principal, ip).thenReturn(ApiResponse.ok(null));
    }

    public record ChangeEmailRequest(@NotBlank @Email String email, @NotBlank @Size(min = 6, max = 6) String emailCode) {
    }

    @PostMapping("/email")
    public Mono<ApiResponse<Void>> changeEmail(@Valid @RequestBody ChangeEmailRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = IpUtils.resolveClientIp(exchange);
        return userSecurityService.changeEmail(principal, req.email(), req.emailCode(), ip).thenReturn(ApiResponse.ok(null));
    }
}
