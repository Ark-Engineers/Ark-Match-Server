package io.arknights.dateorfriends.modules.user.security.service;

import io.arknights.dateorfriends.modules.user.auth.mapper.ActionLogMapper;
import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.Role;
import io.arknights.dateorfriends.tools.security.ban.BanService;
import io.arknights.dateorfriends.tools.security.token.RedisTokenStore;
import io.arknights.dateorfriends.tools.verify.EmailCodeService;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class UserSecurityService {

    private final UserMapper userMapper;
    private final ActionLogMapper actionLogMapper;
    private final EmailCodeService emailCodeService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisTokenStore tokenStore;
    private final BanService banService;

    public UserSecurityService(
            UserMapper userMapper,
            ActionLogMapper actionLogMapper,
            EmailCodeService emailCodeService,
            BCryptPasswordEncoder passwordEncoder,
            RedisTokenStore tokenStore,
            BanService banService
    ) {
        this.userMapper = userMapper;
        this.actionLogMapper = actionLogMapper;
        this.emailCodeService = emailCodeService;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.banService = banService;
    }

    public Mono<Void> updateNickname(JwtPrincipal principal, String nicknameRaw, String ip) {
        assertNotSuperAdmin(principal);
        var nickname = nicknameRaw == null ? "" : nicknameRaw.trim();
        if (nickname.isBlank() || nickname.length() > 64) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "昵称不能为空且长度不能超过64"));
        }
        return Mono.fromCallable(() -> {
                    var user = userMapper.selectById(principal.userId());
                    if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
                    var rows = userMapper.updateNickname(principal.userId(), nickname);
                    if (rows <= 0) throw new BusinessException(ErrorCode.OP_FAILED);
                    actionLogMapper.insert(principal.userId(), ip, "/user/security/nickname");
                    return 0;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<Void> sendChangePasswordCode(JwtPrincipal principal, String ip) {
        assertNotSuperAdmin(principal);
        return Mono.fromCallable(() -> {
                    var user = userMapper.selectById(principal.userId());
                    if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
                    var email = user.getEmail();
                    if (email == null || email.isBlank()) throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未绑定邮箱");
                    return email.trim().toLowerCase();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(email -> banService.checkEmailAllowed(email).then(emailCodeService.sendChangePasswordCode(email, ip)));
    }

    public Mono<Void> changePassword(JwtPrincipal principal, String newPassword, String emailCode, String ip) {
        assertNotSuperAdmin(principal);
        var pwd = newPassword == null ? "" : newPassword.trim();
        if (pwd.length() < 6 || pwd.length() > 64) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "密码长度需在6~64之间"));
        }
        return Mono.fromCallable(() -> {
                    var user = userMapper.selectById(principal.userId());
                    if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
                    var email = user.getEmail();
                    if (email == null || email.isBlank()) throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未绑定邮箱");
                    return email.trim().toLowerCase();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(email -> emailCodeService.verifyChangePasswordCode(email, emailCode, ip)
                        .then(Mono.fromRunnable(() -> {
                                    var hash = passwordEncoder.encode(pwd);
                                    var rows = userMapper.updatePasswordHash(principal.userId(), hash);
                                    if (rows <= 0) throw new BusinessException(ErrorCode.OP_FAILED);
                                    actionLogMapper.insert(principal.userId(), ip, "/user/security/password");
                                })
                                .subscribeOn(Schedulers.boundedElastic()))
                        .then(tokenStore.bumpTokenVersion(principal.userId()))
                        .then(tokenStore.revokeAllRefreshTokens(principal.userId()))
                        .then());
    }

    public Mono<Void> sendChangeEmailCode(JwtPrincipal principal, String ip) {
        assertNotSuperAdmin(principal);
        return Mono.fromCallable(() -> {
                    var user = userMapper.selectById(principal.userId());
                    if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
                    var email = user.getEmail();
                    if (email == null || email.isBlank()) throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未绑定邮箱");
                    return email.trim().toLowerCase();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(email -> banService.checkEmailAllowed(email).then(emailCodeService.sendChangeEmailCode(email, ip)));
    }

    public Mono<Void> changeEmail(JwtPrincipal principal, String newEmail, String emailCode, String ip) {
        assertNotSuperAdmin(principal);
        var email = newEmail == null ? "" : newEmail.trim().toLowerCase();
        if (email.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "邮箱格式不正确"));
        }
        return Mono.fromCallable(() -> {
                    var user = userMapper.selectById(principal.userId());
                    if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
                    var currentEmail = user.getEmail();
                    if (currentEmail == null || currentEmail.isBlank()) throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未绑定邮箱");
                    return currentEmail.trim().toLowerCase();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(currentEmail -> emailCodeService.verifyChangeEmailCode(currentEmail, emailCode, ip)
                        .then(banService.checkEmailAllowed(email))
                        .then(Mono.fromCallable(() -> {
                                    var existed = userMapper.selectByEmailAll(email);
                                    if (existed != null && existed.getDeleted() != null && existed.getDeleted() == 0 && existed.getId() != null && existed.getId() != principal.userId()) {
                                        throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                                    }
                                    var rows = userMapper.updateEmail(principal.userId(), email);
                                    if (rows <= 0) throw new BusinessException(ErrorCode.OP_FAILED);
                                    actionLogMapper.insert(principal.userId(), ip, "/user/security/email");
                                    return 0;
                                })
                                .subscribeOn(Schedulers.boundedElastic()))
                        .then(tokenStore.bumpTokenVersion(principal.userId()))
                        .then(tokenStore.revokeAllRefreshTokens(principal.userId()))
                        .then());
    }

    private void assertNotSuperAdmin(JwtPrincipal principal) {
        var role = principal == null ? null : principal.role();
        if (Role.SUPER_ADMIN.name().equalsIgnoreCase(String.valueOf(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "超级管理员禁止修改个人资料");
        }
    }
}

