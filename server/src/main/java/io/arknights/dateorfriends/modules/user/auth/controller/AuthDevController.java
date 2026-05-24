package io.arknights.dateorfriends.modules.user.auth.controller;

import io.arknights.dateorfriends.tools.verify.EmailCodeService;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import io.arknights.dateorfriends.tools.captcha.CaptchaService;
import org.springframework.beans.factory.annotation.Value;

@Profile("dev")
@RestController
@RequestMapping("/auth/dev")
public class AuthDevController {

    private final EmailCodeService emailCodeService;
    private final CaptchaService captchaService;
    private final ReactiveStringRedisTemplate redis;
    private final Environment environment;
    private final String redisHost;
    private final int redisPort;
    private final int redisDatabase;
    private final String instanceId = UUID.randomUUID().toString();
    private final int serverPort;

    public AuthDevController(
            EmailCodeService emailCodeService,
            CaptchaService captchaService,
            ReactiveStringRedisTemplate redis,
            Environment environment,
            @Value("${spring.data.redis.host:}") String redisHost,
            @Value("${spring.data.redis.port:0}") int redisPort,
            @Value("${spring.data.redis.database:0}") int redisDatabase,
            @Value("${server.port:0}") int serverPort
    ) {
        this.emailCodeService = emailCodeService;
        this.captchaService = captchaService;
        this.redis = redis;
        this.environment = environment;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisDatabase = redisDatabase;
        this.serverPort = serverPort;
    }

    public record TestIssueEmailCodeRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^(register|login)$") String purpose
    ) {
    }

    @PostMapping("/email-code/test")
    public Mono<ApiResponse<EmailCodeService.TestEmailCodeResponse>> testIssueEmailCode(
            @Valid @RequestBody TestIssueEmailCodeRequest req,
            ServerWebExchange exchange
    ) {
        var ip = IpUtils.resolveClientIp(exchange);
        var email = req.email() == null ? "" : req.email().trim();
        var purpose = req.purpose() == null ? "" : req.purpose().trim();
        if (email.isBlank() || purpose.isBlank()) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        }
        return emailCodeService.issueTestCode(purpose, email, ip).map(ApiResponse::ok);
    }

    public record CaptchaCheckRequest(@NotBlank String captchaId) {
    }

    public record CaptchaCheckResponse(boolean exists, long ttlSeconds) {
    }

    @PostMapping("/captcha/check")
    public Mono<ApiResponse<CaptchaCheckResponse>> checkCaptcha(@Valid @RequestBody CaptchaCheckRequest req) {
        return captchaService.debug(req.captchaId())
                .map(r -> ApiResponse.ok(new CaptchaCheckResponse(r.exists(), r.ttlSeconds())));
    }

    public record RedisInfoResponse(String instanceId, int serverPort, String host, int port, int database, String activeProfiles, boolean ok, String error) {
    }

    @GetMapping(value = "/redis/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<RedisInfoResponse>> redisInfo() {
        var profiles = Arrays.toString(environment.getActiveProfiles());
        return redis.hasKey("redis:probe")
                .map(ignored -> ApiResponse.ok(new RedisInfoResponse(instanceId, serverPort, redisHost, redisPort, redisDatabase, profiles, true, "")))
                .onErrorResume(e -> Mono.just(ApiResponse.ok(new RedisInfoResponse(instanceId, serverPort, redisHost, redisPort, redisDatabase, profiles, false, safeError(e)))));
    }

    private String safeError(Throwable e) {
        if (e == null) return "unknown";
        var msg = e.getMessage();
        if (msg == null) msg = "";
        msg = msg.replace("\r", " ").replace("\n", " ").trim();
        if (msg.length() > 200) {
            msg = msg.substring(0, 200);
        }
        return e.getClass().getSimpleName() + (msg.isBlank() ? "" : (": " + msg));
    }
}
