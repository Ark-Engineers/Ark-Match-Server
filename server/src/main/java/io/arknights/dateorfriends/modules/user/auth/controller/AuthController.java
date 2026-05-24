package io.arknights.dateorfriends.modules.user.auth.controller;

import io.arknights.dateorfriends.modules.user.auth.service.AuthService;
import io.arknights.dateorfriends.tools.captcha.CaptchaService;
import io.arknights.dateorfriends.tools.mail.RegistrationMailService;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.verify.EmailCodeService;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController("authController")
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final EmailCodeService emailCodeService;
    private final RegistrationMailService registrationMailService;

    public AuthController(
            AuthService authService,
            CaptchaService captchaService,
            EmailCodeService emailCodeService,
            RegistrationMailService registrationMailService
    ) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.emailCodeService = emailCodeService;
        this.registrationMailService = registrationMailService;
    }

    /**
     * 统一登录入口（管理员/普通用户共用）。
     * 成功后签发 Access Token（2小时）与 Refresh Token（15天）。
     */
    @PostMapping("/login")
    public Mono<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request, ServerWebExchange exchange) {
        var ip = IpUtils.resolveClientIp(exchange);
        return captchaService.verifyKeep(request.captchaId(), request.captchaText())
                .then(authService.login(request.account(), request.password(), ip))
                .flatMap(token -> captchaService.consume(request.captchaId()).thenReturn(token))
                .map(ApiResponse::ok);
    }

    /**
     * 用户注册（公共接口，无需登录）。
     * 成功后创建普通用户账号（role=USER，密码以 BCrypt 哈希存储）。
     */
    @PostMapping("/register")
    public Mono<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request, ServerWebExchange exchange) {
        var ip = IpUtils.resolveClientIp(exchange);
        return emailCodeService.verifyRegisterCode(request.email(), request.emailCode(), ip)
                .then(authService.register(null, request.email(), request.password(), request.nickname(), ip))
                .doOnNext(resp -> registrationMailService
                        .sendRegisterAccountInfo(resp.email(), resp.account(), resp.nickname())
                        .onErrorResume(e -> Mono.empty())
                        .subscribe())
                .map(ApiResponse::ok);
    }

    public record EmailAvailableResponse(boolean available) {
    }

    @GetMapping("/email/available")
    public Mono<ApiResponse<EmailAvailableResponse>> isEmailAvailable(@RequestParam("email") String email) {
        var value = email == null ? "" : email.trim();
        if (value.isBlank() || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        }
        return authService.isEmailAvailable(value).map(ok -> ApiResponse.ok(new EmailAvailableResponse(ok)));
    }

    public record CaptchaResponse(String captchaId, String svg) {
    }

    @GetMapping("/captcha/new")
    public Mono<ApiResponse<CaptchaResponse>> newCaptcha(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        exchange.getResponse().getHeaders().set(HttpHeaders.PRAGMA, "no-cache");
        return captchaService.create().map(c -> ApiResponse.ok(new CaptchaResponse(c.captchaId(), c.svg())));
    }

    public record SendRegisterEmailCodeRequest(@NotBlank @Email String email) {
    }

    @PostMapping("/register/email-code/send")
    public Mono<ApiResponse<Void>> sendRegisterEmailCode(@Valid @RequestBody SendRegisterEmailCodeRequest req, ServerWebExchange exchange) {
        var ip = IpUtils.resolveClientIp(exchange);
        return emailCodeService.sendRegisterCode(req.email(), ip).thenReturn(ApiResponse.ok(null));
    }

    public record SendLoginEmailCodeRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 128) String captchaId,
            @NotBlank @Size(max = 16) String captchaText
    ) {
    }

    @PostMapping("/login/email-code/send")
    public Mono<ApiResponse<Void>> sendLoginEmailCode(@Valid @RequestBody SendLoginEmailCodeRequest req, ServerWebExchange exchange) {
        var ip = IpUtils.resolveClientIp(exchange);
        return captchaService.verifyKeep(req.captchaId(), req.captchaText())
                .then(emailCodeService.sendLoginCode(req.email(), ip))
                .thenReturn(ApiResponse.ok(null));
    }

    public record EmailCodeLoginRequest(@NotBlank @Email String email, @NotBlank @Size(min = 6, max = 6) String emailCode) {
    }

    @PostMapping("/login/email-code/verify")
    public Mono<ApiResponse<TokenResponse>> loginByEmailCode(@Valid @RequestBody EmailCodeLoginRequest req, ServerWebExchange exchange) {
        var ip = IpUtils.resolveClientIp(exchange);
        return emailCodeService.verifyLoginCode(req.email(), req.emailCode(), ip)
                .then(authService.loginByEmailCode(req.email(), ip))
                .map(ApiResponse::ok);
    }

    /**
     * 刷新令牌入口（仅 Refresh Token 可用）。
     * 每次刷新都会签发新的 Access/Refresh，并作废旧 Refresh（Redis 立即失效 + 黑名单）。
     */
    @PostMapping("/refresh")
    public Mono<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken()).map(ApiResponse::ok);
    }

    /**
     * 登出（主动吊销当前 Access Token）。
     * 可选携带 refreshToken 以同时吊销该 Refresh Token。
     */
    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout(@RequestBody(required = false) LogoutRequest request, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) {
            return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        }
        var accessToken = exchange.<String>getAttribute("auth.token");
        if (accessToken == null) {
            accessToken = resolveBearerToken(exchange);
        }
        var refreshToken = request == null ? null : request.refreshToken();
        return authService.logout(principal, accessToken, refreshToken).thenReturn(ApiResponse.ok(null));
    }

    /**
     * 登出全部（吊销该用户全部端的 Access/Refresh）。
     * 通过 Redis 令牌版本号机制，使历史令牌即时失效。
     */
    @PostMapping("/logout-all")
    public Mono<ApiResponse<Void>> logoutAll(ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) {
            return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        }
        return authService.logoutAll(principal.userId()).thenReturn(ApiResponse.ok(null));
    }

    private String resolveBearerToken(ServerWebExchange exchange) {
        var auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return auth.substring("Bearer ".length()).trim();
    }
}
