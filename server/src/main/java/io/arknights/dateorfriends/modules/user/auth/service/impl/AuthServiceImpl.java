package io.arknights.dateorfriends.modules.user.auth.service.impl;

import io.arknights.dateorfriends.modules.user.auth.controller.TokenResponse;
import io.arknights.dateorfriends.modules.user.auth.controller.RegisterResponse;
import io.arknights.dateorfriends.modules.user.auth.mapper.ActionLogMapper;
import io.arknights.dateorfriends.modules.user.auth.mapper.UserDO;
import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import io.arknights.dateorfriends.modules.user.auth.service.AuthService;
import io.arknights.dateorfriends.tools.jwt.JwtService;
import io.arknights.dateorfriends.tools.jwt.JwtTokenType;
import io.arknights.dateorfriends.tools.security.ban.BanService;
import io.arknights.dateorfriends.tools.security.Role;
import io.arknights.dateorfriends.tools.security.SecurityProperties;
import io.arknights.dateorfriends.tools.security.token.RedisTokenStore;
import io.arknights.dateorfriends.tools.web.IpUtils;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service("authService")
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final ActionLogMapper actionLogMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisTokenStore tokenStore;
    private final SecurityProperties securityProperties;
    private final io.arknights.dateorfriends.tools.jwt.JwtProperties jwtProperties;
    private final BanService banService;
    private final SecureRandom random = new SecureRandom();

    public AuthServiceImpl(
            UserMapper userMapper,
            ActionLogMapper actionLogMapper,
            BCryptPasswordEncoder passwordEncoder,
            JwtService jwtService,
            RedisTokenStore tokenStore,
            SecurityProperties securityProperties,
            io.arknights.dateorfriends.tools.jwt.JwtProperties jwtProperties,
            BanService banService
    ) {
        this.userMapper = userMapper;
        this.actionLogMapper = actionLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.securityProperties = securityProperties;
        this.jwtProperties = jwtProperties;
        this.banService = banService;
    }

    @Override
    public Mono<TokenResponse> login(String account, String password, String ip) {
        return findUserByAccountOrEmail(account)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND)))
                .flatMap(user -> banService.checkEmailAllowed(user.getEmail())
                        .then(checkAndHandleLock(user))
                        .then(checkAccountStatus(user))
                        .then(verifyPassword(user, password))
                        .flatMap(ok -> {
                            if (!ok) {
                                return handleLoginFail(user).then(Mono.error(new BusinessException(ErrorCode.PASSWORD_INCORRECT)));
                            }
                            return handleLoginSuccess(user, ip).then(issueTokens(user));
                        }));
    }

    @Override
    public Mono<TokenResponse> loginByEmailCode(String email, String ip) {
        return findUserByAccountOrEmail(email)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND)))
                .flatMap(user -> banService.checkEmailAllowed(user.getEmail())
                        .then(checkAndHandleLock(user))
                        .then(checkAccountStatus(user))
                        .then(handleLoginSuccess(user, ip))
                        .then(issueTokens(user)));
    }

    @Override
    public Mono<Boolean> isEmailAvailable(String email) {
        return Mono.fromCallable(() -> userMapper.selectByEmail(email) == null)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<RegisterResponse> register(String account, String email, String password, String nickname, String ip) {
        var baseName = nickname == null ? "" : nickname.trim();
        if (baseName.isBlank()) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        }
        return banService.checkEmailAllowed(email)
                .then(Mono.fromCallable(() -> {
                            var existed = userMapper.selectByEmailAll(email);
                            if (existed != null && existed.getDeleted() != null && existed.getDeleted() == 0) {
                                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                            }

                            var passwordHash = passwordEncoder.encode(password);
                            var finalNickname = buildNickname(baseName);

                            if (existed != null && existed.getDeleted() != null && existed.getDeleted() == 1) {
                                for (int i = 0; i < 30; i++) {
                                    var generated = randomAccount();
                                    if (userMapper.countByAccountAll(generated) > 0) {
                                        continue;
                                    }
                                    userMapper.restoreForReRegister(existed.getId(), generated, passwordHash, finalNickname);
                                    existed.setAccount(generated);
                                    existed.setPasswordHash(passwordHash);
                                    existed.setNickname(finalNickname);
                                    existed.setDeleted(0);
                                    existed.setDeletedAt(null);
                                    existed.setStatus("NORMAL");
                                    existed.setRole("USER");
                                    existed.setLoginFailCount(0);
                                    existed.setLockedUntil(null);
                                    return existed;
                                }
                                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "账号生成失败");
                            }

                            var user = new UserDO();
                            user.setEmail(email);
                            user.setPasswordHash(passwordHash);
                            user.setNickname(finalNickname);

                            for (int i = 0; i < 30; i++) {
                                var generated = randomAccount();
                                if (userMapper.countByAccountAll(generated) > 0) {
                                    continue;
                                }
                                user.setAccount(generated);
                                try {
                                    userMapper.insertUser(user);
                                    return user;
                                } catch (DuplicateKeyException e) {
                                    continue;
                                } catch (Exception e) {
                                    var msg = e.getMessage();
                                    if (msg != null && (msg.contains("Duplicate") || msg.contains("duplicate") || msg.contains("uk_user_account"))) {
                                        continue;
                                    }
                                    throw e;
                                }
                            }
                            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "账号生成失败");
                        }).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(user -> insertActionLog(user.getId(), ip, "/auth/register")
                        .thenReturn(new RegisterResponse(user.getId(), user.getAccount(), user.getEmail(), user.getNickname())));
    }

    private String randomAccount() {
        var len = 5 + random.nextInt(6);
        var sb = new StringBuilder(len);
        sb.append((char) ('1' + random.nextInt(9)));
        for (int i = 1; i < len; i++) {
            sb.append((char) ('0' + random.nextInt(10)));
        }
        return sb.toString();
    }

    private String buildNickname(String baseNameRaw) {
        var baseName = baseNameRaw == null ? "" : baseNameRaw.trim();
        if (baseName.isBlank()) baseName = "用户";
        var suffix = "%04d".formatted(random.nextInt(10_000));
        var maxBase = 64 - 1 - 4;
        if (baseName.length() > maxBase) {
            baseName = baseName.substring(0, maxBase);
        }
        return baseName + "#" + suffix;
    }

    @Override
    public Mono<TokenResponse> refresh(String refreshToken) {
        io.arknights.dateorfriends.tools.jwt.JwtPrincipal principal;
        try {
            principal = jwtService.parseAndValidate(refreshToken, JwtTokenType.REFRESH);
        } catch (Exception e) {
            return Mono.error(new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        }
        return tokenStore.isBlacklisted(principal.jti())
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return Mono.error(new BusinessException(ErrorCode.TOKEN_REVOKED));
                    }
                    return tokenStore.getTokenVersion(principal.userId())
                            .flatMap(ver -> {
                                if (ver != principal.tokenVersion()) {
                                    return Mono.error(new BusinessException(ErrorCode.TOKEN_REVOKED));
                                }
                                return tokenStore.isRefreshTokenValid(principal.userId(), principal.jti())
                                        .flatMap(valid -> {
                                            if (!Boolean.TRUE.equals(valid)) {
                                                return Mono.error(new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
                                            }
                                            return tokenStore.revokeRefreshToken(principal.userId(), principal.jti())
                                                    .then(tokenStore.blacklist(principal.jti(), remainingTtl(principal.expiresAt())))
                                                    .then(findUserById(principal.userId()))
                                                    .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND)))
                                                    .flatMap(user -> banService.checkEmailAllowed(user.getEmail()).then(issueTokens(user)));
                                        });
                            });
                });
    }

    @Override
    public Mono<Void> logout(io.arknights.dateorfriends.tools.jwt.JwtPrincipal principal, String accessToken, String refreshToken) {
        var tasks = tokenStore.blacklist(principal.jti(), remainingTtl(principal.expiresAt()));
        if (refreshToken != null && !refreshToken.isBlank()) {
            tasks = tasks.then(revokeRefreshTokenIfMatch(principal.userId(), refreshToken));
        }
        return tasks.then(insertActionLog(principal.userId(), "unknown", "logout")).then();
    }

    @Override
    public Mono<Void> logoutAll(long userId) {
        return tokenStore.bumpTokenVersion(userId)
                .then(tokenStore.revokeAllRefreshTokens(userId))
                .then(insertActionLog(userId, "unknown", "logout-all"))
                .then();
    }

    private Mono<UserDO> findUserByAccountOrEmail(String accountOrEmail) {
        return Mono.fromCallable(() -> {
                    if (isEmail(accountOrEmail)) {
                        return userMapper.selectByEmail(accountOrEmail);
                    }
                    return userMapper.selectByAccount(accountOrEmail);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private boolean isEmail(String value) {
        if (value == null) return false;
        var s = value.trim();
        if (s.isBlank()) return false;
        return s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private Mono<UserDO> findUserById(long userId) {
        return Mono.fromCallable(() -> userMapper.selectById(userId)).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> checkAndHandleLock(UserDO user) {
        if (user.getLockedUntil() == null) {
            return Mono.empty();
        }
        var now = LocalDateTime.now();
        if (user.getLockedUntil().isAfter(now)) {
            return Mono.error(new BusinessException(ErrorCode.ACCOUNT_LOCKED));
        }
        return Mono.fromRunnable(() -> userMapper.updateLoginFailState(user.getId(), 0, null))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Boolean> verifyPassword(UserDO user, String rawPassword) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> handleLoginFail(UserDO user) {
        if (user.getStatus() == null) {
            return Mono.empty();
        }
        if ("SUSPENDED".equals(user.getStatus())) {
            return Mono.error(new BusinessException(ErrorCode.ACCOUNT_SUSPENDED));
        }
        if ("BANNED".equals(user.getStatus())) {
            return Mono.error(new BusinessException(ErrorCode.ACCOUNT_BANNED));
        }
        var count = user.getLoginFailCount() == null ? 0 : user.getLoginFailCount();
        var newCount = count + 1;
        LocalDateTime lockedUntil = null;
        if (newCount >= securityProperties.getLoginFailLockThreshold()) {
            lockedUntil = LocalDateTime.now().plusMinutes(securityProperties.getLoginFailLockMinutes());
        }
        var finalLockedUntil = lockedUntil;
        return Mono.fromRunnable(() -> userMapper.updateLoginFailState(user.getId(), newCount, finalLockedUntil))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(() -> {
                    if (finalLockedUntil != null) {
                        return Mono.error(new BusinessException(ErrorCode.ACCOUNT_LOCKED));
                    }
                    return Mono.empty();
                }));
    }

    private Mono<Void> handleLoginSuccess(UserDO user, String ip) {
        if ("SUSPENDED".equals(user.getStatus())) return Mono.error(new BusinessException(ErrorCode.ACCOUNT_SUSPENDED));
        if ("BANNED".equals(user.getStatus())) return toBannedError(user.getId());
        var now = LocalDateTime.now();
        var existed = user.getLastLoginIp();
        String normalized = ip == null ? null : ip.trim();
        boolean shouldUpdateIp = normalized != null
                && !normalized.isBlank()
                && !"unknown".equalsIgnoreCase(normalized)
                && (existed == null || existed.isBlank() || !existed.equals(normalized))
                && (IpUtils.isPublicIp(normalized) || existed == null || existed.isBlank() || !IpUtils.isPublicIp(existed));
        String ipToSave = shouldUpdateIp ? normalized : existed;
        return Mono.fromRunnable(() -> userMapper.updateLoginSuccessState(user.getId(), now, ipToSave))
                .subscribeOn(Schedulers.boundedElastic())
                .then(insertActionLog(user.getId(), ip, "/auth/login"));
    }

    private Mono<Void> checkAccountStatus(UserDO user) {
        if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
        if ("SUSPENDED".equals(user.getStatus())) return Mono.error(new BusinessException(ErrorCode.ACCOUNT_SUSPENDED));
        if ("BANNED".equals(user.getStatus())) return toBannedError(user.getId());
        return Mono.empty();
    }

    private Mono<Void> toBannedError(Long userId) {
        if (userId == null) return Mono.error(new BusinessException(ErrorCode.ACCOUNT_BANNED));
        return banService.getUserBanBlockInfo(userId)
                .defaultIfEmpty(new io.arknights.dateorfriends.tools.security.ban.BanBlockInfo(null, null, null, null, null))
                .flatMap(info -> Mono.error(new BusinessException(ErrorCode.ACCOUNT_BANNED, ErrorCode.ACCOUNT_BANNED.defaultMessage(), info)));
    }

    private Mono<TokenResponse> issueTokens(UserDO user) {
        var role = user.getRole();
        var weight = roleWeight(role);
        return tokenStore.getTokenVersion(user.getId())
                .flatMap(ver -> {
                    var access = jwtService.createAccessToken(user.getId(), role, weight, ver);
                    var refresh = jwtService.createRefreshToken(user.getId(), role, weight, ver);
                    var refreshPrincipal = jwtService.parseAndValidate(refresh, JwtTokenType.REFRESH);
                    return tokenStore.storeRefreshToken(user.getId(), refreshPrincipal.jti(), Duration.ofSeconds(jwtProperties.getRefreshTtlSeconds()))
                            .thenReturn(new TokenResponse(
                                    "Bearer",
                                    access,
                                    jwtProperties.getAccessTtlSeconds(),
                                    refresh,
                                    jwtProperties.getRefreshTtlSeconds(),
                                    user.getId(),
                                    role,
                                    weight
                            ));
                });
    }

    private int roleWeight(String role) {
        if (Role.SUPER_ADMIN.name().equals(role)) {
            return 1000;
        }
        if (Role.ADMIN.name().equals(role)) {
            return 100;
        }
        return 10;
    }

    private Duration remainingTtl(Instant expiresAt) {
        var now = Instant.now();
        if (expiresAt.isBefore(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, expiresAt);
    }

    private Mono<Void> revokeRefreshTokenIfMatch(long userId, String refreshToken) {
        io.arknights.dateorfriends.tools.jwt.JwtPrincipal refreshPrincipal;
        try {
            refreshPrincipal = jwtService.parseAndValidate(refreshToken, JwtTokenType.REFRESH);
        } catch (Exception e) {
            return Mono.empty();
        }
        if (refreshPrincipal.userId() != userId) {
            return Mono.empty();
        }
        return tokenStore.revokeRefreshToken(userId, refreshPrincipal.jti())
                .then(tokenStore.blacklist(refreshPrincipal.jti(), remainingTtl(refreshPrincipal.expiresAt())))
                .then();
    }

    private Mono<Void> insertActionLog(long userId, String ip, String api) {
        return Mono.fromRunnable(() -> actionLogMapper.insert(userId, ip, api))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
