package io.arknights.dateorfriends.tools.security.ban;

import io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordDO;
import io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordMapper;
import io.arknights.dateorfriends.modules.admin.ban.mapper.BanOperationLogDO;
import io.arknights.dateorfriends.modules.admin.ban.mapper.BanOperationLogMapper;
import io.arknights.dateorfriends.modules.user.auth.mapper.ActionLogMapper;
import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import io.arknights.dateorfriends.tools.security.token.RedisTokenStore;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class BanService {
    private final BanRecordMapper banRecordMapper;
    private final BanOperationLogMapper banOperationLogMapper;
    private final UserMapper userMapper;
    private final ActionLogMapper actionLogMapper;
    private final RedisBanStore banStore;
    private final RedisTokenStore tokenStore;
    private final BanProperties banProperties;

    public BanService(
            BanRecordMapper banRecordMapper,
            BanOperationLogMapper banOperationLogMapper,
            UserMapper userMapper,
            ActionLogMapper actionLogMapper,
            RedisBanStore banStore,
            RedisTokenStore tokenStore,
            BanProperties banProperties
    ) {
        this.banRecordMapper = banRecordMapper;
        this.banOperationLogMapper = banOperationLogMapper;
        this.userMapper = userMapper;
        this.actionLogMapper = actionLogMapper;
        this.banStore = banStore;
        this.tokenStore = tokenStore;
        this.banProperties = banProperties;
    }

    private void insertOperationLog(long recordId, Long actorId, String actorRole, String actionType, String fromStatus, String toStatus) {
        var log = new BanOperationLogDO();
        log.setRecordId(recordId);
        log.setActorId(actorId);
        log.setActorRole(actorRole);
        log.setActionType(actionType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setCreatedAt(LocalDateTime.now());
        banOperationLogMapper.insert(log);
    }

    public Mono<Void> checkIpAllowed(String ip) {
        if (isWhitelistedIp(ip)) return Mono.empty();
        return banStore.isIpBanned(ip).flatMap(banned -> banned ? Mono.error(new BusinessException(ErrorCode.IP_BANNED)) : Mono.empty());
    }

    public Mono<Void> checkEmailAllowed(String email) {
        if (isWhitelistedEmail(email)) return Mono.empty();
        return banStore.isEmailBanned(email)
                .flatMap(banned -> banned ? Mono.error(new BusinessException(ErrorCode.EMAIL_BANNED)) : Mono.empty());
    }

    public Mono<Void> checkUserAllowed(long userId) {
        return banStore.isUserBanned(userId)
                .flatMap(banned -> {
                    if (!Boolean.TRUE.equals(banned)) return Mono.empty();
                    return getUserBanBlockInfo(userId)
                            .defaultIfEmpty(new BanBlockInfo(null, null, null, null, null))
                            .flatMap(info -> Mono.error(new BusinessException(ErrorCode.ACCOUNT_BANNED, ErrorCode.ACCOUNT_BANNED.defaultMessage(), info)));
                });
    }

    public Mono<BanBlockInfo> getUserBanBlockInfo(long userId) {
        if (userId <= 0) return Mono.empty();
        return Mono.fromCallable(() -> {
                    var list = banRecordMapper.selectListForUser(userId, "ACTIVE", 1, 0);
                    if (list == null || list.isEmpty()) return null;
                    return list.get(0);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(record -> {
                    if (record == null) return Mono.empty();
                    var now = LocalDateTime.now();
                    Long remainingSeconds = null;
                    Integer remainingDays = null;
                    if (record.getExpiresAt() != null) {
                        var seconds = Duration.between(now, record.getExpiresAt()).getSeconds();
                        if (seconds < 0) seconds = 0;
                        remainingSeconds = seconds;
                        remainingDays = (int) ((seconds + 86399) / 86400);
                    }
                    return Mono.just(new BanBlockInfo(
                            record.getReason(),
                            record.getEffectiveAt(),
                            record.getExpiresAt(),
                            remainingSeconds,
                            remainingDays
                    ));
                });
    }

    public Mono<BanRecordDO> banIp(long adminId, String ip, String reason, Long durationSeconds) {
        return banIpInternal(adminId, ip, reason, durationSeconds, null);
    }

    private Mono<BanRecordDO> banIpInternal(long adminId, String ip, String reason, Long durationSeconds, Long bannedUserId) {
        if (isWhitelistedIp(ip)) {
            return Mono.error(new BusinessException(ErrorCode.BAN_TARGET_WHITELISTED));
        }
        var normalized = ip == null ? null : ip.trim();
        var now = LocalDateTime.now();
        var expiresAt = durationSeconds == null ? null : now.plusSeconds(durationSeconds);
        var record = new BanRecordDO();
        record.setTargetType("IP");
        record.setTargetValue(normalized);
        record.setBannedUserId(bannedUserId);
        record.setAdminId(adminId);
        record.setReason(reason);
        record.setDurationSeconds(durationSeconds);
        record.setEffectiveAt(now);
        record.setExpiresAt(expiresAt);
        record.setStatus("ACTIVE");

        var ttl = durationSeconds == null ? null : Duration.ofSeconds(durationSeconds);
        return banStore.isIpBanned(normalized)
                .flatMap(already -> {
                    if (Boolean.TRUE.equals(already)) {
                        return Mono.error(new BusinessException(ErrorCode.ALREADY_BANNED));
                    }
                    return Mono.fromCallable(() -> {
                                banRecordMapper.insert(record);
                                insertOperationLog(record.getId(), adminId, "ADMIN", "BAN", null, "ACTIVE");
                                return record;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(r -> banStore.banIp(normalized, ttl).thenReturn(r));
                });
    }

    public Mono<List<BanRecordDO>> banIpBatch(long adminId, List<String> ips, String reason, Long durationSeconds, boolean confirm) {
        if (!confirm) {
            return Mono.error(new BusinessException(ErrorCode.BATCH_CONFIRM_REQUIRED));
        }
        return Flux.fromIterable(ips)
                .filter(ip -> ip != null && !ip.isBlank())
                .concatMap(ip -> banIp(adminId, ip.trim(), reason, durationSeconds))
                .collectList();
    }

    public Mono<BanRecordDO> banEmail(long adminId, String email, String reason, Long durationSeconds) {
        var normalized = email == null ? null : email.trim().toLowerCase();
        if (isWhitelistedEmail(normalized)) {
            return Mono.error(new BusinessException(ErrorCode.BAN_TARGET_WHITELISTED));
        }
        var now = LocalDateTime.now();
        var expiresAt = durationSeconds == null ? null : now.plusSeconds(durationSeconds);
        var record = new BanRecordDO();
        record.setTargetType("EMAIL");
        record.setTargetValue(normalized);
        record.setAdminId(adminId);
        record.setReason(reason);
        record.setDurationSeconds(durationSeconds);
        record.setEffectiveAt(now);
        record.setExpiresAt(expiresAt);
        record.setStatus("ACTIVE");

        var ttl = durationSeconds == null ? null : Duration.ofSeconds(durationSeconds);
        return banStore.isEmailBanned(normalized)
                .flatMap(already -> {
                    if (Boolean.TRUE.equals(already)) {
                        return Mono.error(new BusinessException(ErrorCode.ALREADY_BANNED));
                    }
                    return Mono.fromCallable(() -> {
                                var user = userMapper.selectByEmail(normalized);
                                Long userId = null;
                                if (user != null && user.getId() != null) {
                                    userId = user.getId();
                                    record.setBannedUserId(userId);
                                }
                                banRecordMapper.insert(record);
                                insertOperationLog(record.getId(), adminId, "ADMIN", "BAN", null, "ACTIVE");
                                return userId;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(userId -> banStore.banEmail(normalized, ttl).thenReturn(userId))
                            .flatMap(userId -> {
                                if (userId == null) return Mono.just(record);
                                return tokenStore.bumpTokenVersion(userId)
                                        .then(tokenStore.revokeAllRefreshTokens(userId))
                                        .then(Mono.fromRunnable(() -> userMapper.updateStatus(userId, "SUSPENDED"))
                                                .subscribeOn(Schedulers.boundedElastic()))
                                        .thenReturn(record);
                            });
                });
    }

    public Mono<BanRecordDO> banUser(long adminId, long userId, String reason, Long durationSeconds) {
        if (userId <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        var now = LocalDateTime.now();
        var expiresAt = durationSeconds == null ? null : now.plusSeconds(durationSeconds);

        return Mono.fromCallable(() -> userMapper.selectById(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null || user.getEmail() == null) {
                        return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    }
                    if (isWhitelistedEmail(user.getEmail())) {
                        return Mono.error(new BusinessException(ErrorCode.BAN_TARGET_WHITELISTED));
                    }
                    var record = new BanRecordDO();
                    record.setTargetType("USER");
                    record.setTargetValue(String.valueOf(userId));
                    record.setBannedUserId(userId);
                    record.setAdminId(adminId);
                    record.setReason(reason);
                    record.setDurationSeconds(durationSeconds);
                    record.setEffectiveAt(now);
                    record.setExpiresAt(expiresAt);
                    record.setStatus("ACTIVE");

                    var ttl = durationSeconds == null ? null : Duration.ofSeconds(durationSeconds);
                    return banStore.isUserBanned(userId)
                            .flatMap(already -> {
                                if (Boolean.TRUE.equals(already)) {
                                    return Mono.error(new BusinessException(ErrorCode.ALREADY_BANNED));
                                }
                                return Mono.fromCallable(() -> {
                                            banRecordMapper.insert(record);
                                            insertOperationLog(record.getId(), adminId, "ADMIN", "BAN", null, "ACTIVE");
                                            return record;
                                        })
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .flatMap(r -> banStore.banUser(userId, ttl).thenReturn(r))
                                        .flatMap(r -> tokenStore.bumpTokenVersion(userId)
                                                .then(tokenStore.revokeAllRefreshTokens(userId))
                                                .then(Mono.fromRunnable(() -> userMapper.updateStatus(userId, "BANNED"))
                                                        .subscribeOn(Schedulers.boundedElastic()))
                                                .thenReturn(r));
                            });
                });
    }

    public Mono<List<UserSummary>> searchUsers(Long userId, String account, String nickname, String email, String ip, int limit) {
        var safeLimit = Math.min(200, Math.max(1, limit));
        var hasAny = (userId != null && userId > 0)
                || (account != null && !account.isBlank())
                || (nickname != null && !nickname.isBlank())
                || (email != null && !email.isBlank())
                || (ip != null && !ip.isBlank());
        if (!hasAny) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        }

        return Mono.fromCallable(() -> {
                    List<UserSummary> results = new ArrayList<>();
                    if (userId != null && userId > 0) {
                        var u = userMapper.selectById(userId);
                        if (u != null) results.add(toSummary(u));
                    }
                    if (results.isEmpty() && account != null && !account.isBlank()) {
                        var list = userMapper.selectListByAccountLike(account.trim(), safeLimit);
                        for (var u : list) {
                            if (u == null) continue;
                            var summary = toSummary(u);
                            if (results.stream().noneMatch(x -> x.userId() == summary.userId())) {
                                results.add(summary);
                            }
                        }
                    }
                    if (results.isEmpty() && nickname != null && !nickname.isBlank()) {
                        var list = userMapper.selectListByNicknameLike(nickname.trim(), safeLimit);
                        for (var u : list) {
                            if (u == null) continue;
                            var summary = toSummary(u);
                            if (results.stream().noneMatch(x -> x.userId() == summary.userId())) {
                                results.add(summary);
                            }
                        }
                    }
                    if (results.isEmpty() && email != null && !email.isBlank()) {
                        var list = userMapper.selectListByEmailLike(email.trim().toLowerCase(), safeLimit);
                        for (var u : list) {
                            if (u == null) continue;
                            var summary = toSummary(u);
                            if (results.stream().noneMatch(x -> x.userId() == summary.userId())) {
                                results.add(summary);
                            }
                        }
                    }
                    if (results.isEmpty() && ip != null && !ip.isBlank()) {
                        var ipValue = ip.trim();
                        var list = userMapper.selectListByLastLoginIpLike(ipValue, safeLimit);
                        for (var u : list) results.add(toSummary(u));

                        var idsFromLog = actionLogMapper.selectDistinctUserIdsByIps(List.of(ipValue));
                        if (idsFromLog != null && !idsFromLog.isEmpty()) {
                            var users = userMapper.selectListByIds(idsFromLog);
                            for (var u : users) {
                                var summary = toSummary(u);
                                if (results.stream().noneMatch(x -> x.userId() == summary.userId())) {
                                    results.add(summary);
                                }
                            }
                        }
                    }
                    for (var r : results) {
                        var ips = actionLogMapper.selectDistinctIpsByUserId(r.userId());
                        r.relatedIps().addAll(ips);
                        if (r.lastLoginIp() != null && !r.lastLoginIp().isBlank() && !r.relatedIps().contains(r.lastLoginIp())) {
                            r.relatedIps().add(r.lastLoginIp());
                        }
                    }
                    return results;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<BanRecordDO>> banUserAssociatedIps(long adminId, long userId, String reason, Long durationSeconds) {
        return Mono.fromCallable(() -> {
                    var user = userMapper.selectById(userId);
                    if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                    var ips = new ArrayList<String>();
                    if (user.getLastLoginIp() != null && !user.getLastLoginIp().isBlank()) ips.add(user.getLastLoginIp());
                    var fromLog = actionLogMapper.selectDistinctIpsByUserId(userId);
                    for (var i : fromLog) {
                        if (i != null && !i.isBlank() && !ips.contains(i)) ips.add(i);
                    }
                    return ips;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .concatMap(ip -> banIpInternal(adminId, ip, reason, durationSeconds, userId).onErrorResume(BusinessException.class, e -> {
                    if (e.getErrorCode() == ErrorCode.ALREADY_BANNED) return Mono.empty();
                    return Mono.error(e);
                }))
                .collectList();
    }

    public Mono<List<BanRecordDO>> banUserEmailOnly(long adminId, long userId, String reason, Long durationSeconds) {
        return Mono.fromCallable(() -> userMapper.selectById(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null) return Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND));
                    return banEmail(adminId, user.getEmail(), reason, durationSeconds).map(List::of);
                });
    }

    public Mono<List<BanRecordDO>> banUserFull(long adminId, long userId, String reason, Long durationSeconds, boolean confirm) {
        if (!confirm) return Mono.error(new BusinessException(ErrorCode.BATCH_CONFIRM_REQUIRED));
        return Mono.fromCallable(() -> {
                    var user = userMapper.selectById(userId);
                    if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                    var ips = new ArrayList<String>();
                    if (user.getLastLoginIp() != null && !user.getLastLoginIp().isBlank()) ips.add(user.getLastLoginIp());
                    var fromLog = actionLogMapper.selectDistinctIpsByUserId(userId);
                    for (var i : fromLog) {
                        if (i != null && !i.isBlank() && !ips.contains(i)) ips.add(i);
                    }
                    return ips;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ips -> {
                    var relatedUserIds = ips.isEmpty()
                            ? Mono.just(List.<Long>of(userId))
                            : Mono.fromCallable(() -> actionLogMapper.selectDistinctUserIdsByIps(ips))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .map(list -> {
                                        var ids = new ArrayList<Long>();
                                        if (list != null) ids.addAll(list);
                                        if (!ids.contains(userId)) ids.add(userId);
                                        return ids;
                                    });

                    return relatedUserIds.flatMapMany(ids -> Flux.fromIterable(ids))
                            .concatMap(id -> banUser(adminId, id, reason, durationSeconds).onErrorResume(BusinessException.class, e -> {
                                if (e.getErrorCode() == ErrorCode.ALREADY_BANNED) return Mono.empty();
                                if (e.getErrorCode() == ErrorCode.BAN_TARGET_WHITELISTED) return Mono.empty();
                                return Mono.error(e);
                            }))
                            .collectList()
                            .flatMap(records -> banUserAssociatedIps(adminId, userId, reason, durationSeconds)
                                    .map(ipRecords -> {
                                        var all = new ArrayList<BanRecordDO>();
                                        all.addAll(records);
                                        all.addAll(ipRecords);
                                        return all;
                                    }))
                            .flatMap(records -> banUserEmailOnly(adminId, userId, reason, durationSeconds)
                                    .map(emailRecords -> {
                                        var all = new ArrayList<BanRecordDO>();
                                        all.addAll(records);
                                        all.addAll(emailRecords);
                                        return all;
                                    }));
                });
    }

    public Mono<Void> assertBanPermission(io.arknights.dateorfriends.tools.jwt.JwtPrincipal principal) {
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        if (principal.weight() < banProperties.getMinAdminWeight()) {
            return Mono.error(new BusinessException(ErrorCode.BAN_PERMISSION_REQUIRED));
        }
        return Mono.empty();
    }

    public Mono<Void> writeAdminActionLog(long adminId, String ip, String api) {
        return Mono.fromRunnable(() -> actionLogMapper.insert(adminId, ip == null ? "unknown" : ip, api))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public record UserSummary(
            long userId,
            String account,
            String nickname,
            String email,
            String role,
            String status,
            String lastLoginIp,
            java.util.List<String> relatedIps
    ) {
        public java.util.List<String> relatedIps() {
            return relatedIps;
        }
    }

    private UserSummary toSummary(io.arknights.dateorfriends.modules.user.auth.mapper.UserDO u) {
        return new UserSummary(
                u.getId(),
                u.getAccount(),
                u.getNickname(),
                u.getEmail(),
                u.getRole(),
                u.getStatus(),
                u.getLastLoginIp(),
                new java.util.ArrayList<>()
        );
    }

    public Mono<List<BanRecordDO>> banUserBatch(long adminId, List<Long> userIds, String reason, Long durationSeconds, boolean confirm) {
        if (!confirm) {
            return Mono.error(new BusinessException(ErrorCode.BATCH_CONFIRM_REQUIRED));
        }
        return Flux.fromIterable(userIds == null ? List.<Long>of() : userIds)
                .filter(id -> id != null && id > 0)
                .concatMap(id -> banUser(adminId, id, reason, durationSeconds))
                .collectList();
    }

    public Mono<List<BanRecordDO>> banEmailBatch(long adminId, List<String> emails, String reason, Long durationSeconds, boolean confirm) {
        if (!confirm) {
            return Mono.error(new BusinessException(ErrorCode.BATCH_CONFIRM_REQUIRED));
        }
        return Flux.fromIterable(emails)
                .filter(e -> e != null && !e.isBlank())
                .concatMap(e -> banEmail(adminId, e, reason, durationSeconds))
                .collectList();
    }

    public Mono<BanRecordDO> unbanByRecordId(long adminId, long recordId) {
        var now = LocalDateTime.now();
        return Mono.fromCallable(() -> banRecordMapper.selectById(recordId))
                .subscribeOn(Schedulers.boundedElastic())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PARAM_INVALID)))
                .flatMap(record -> {
                    if (record == null) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
                    if (!"ACTIVE".equalsIgnoreCase(record.getStatus())) return Mono.just(record);
                    return Mono.fromCallable(() -> {
                                banRecordMapper.updateUnbanState(recordId, "REVOKED", now, adminId, "MANUAL");
                                insertOperationLog(recordId, adminId, "ADMIN", "UNBAN_MANUAL", "ACTIVE", "REVOKED");
                                return record;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .then(unbanTarget(record))
                            .then(refreshUserStatusIfNeeded(record))
                            .thenReturn(record);
                });
    }

    public Mono<Void> expireSweepOnce() {
        var now = LocalDateTime.now();
        return Mono.fromCallable(() -> banRecordMapper.selectExpiredActive(now, banProperties.getSweepBatchSize()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Mono::justOrEmpty)
                .flatMapIterable(list -> list)
                .concatMap(record -> Mono.fromCallable(() -> {
                            banRecordMapper.updateUnbanState(record.getId(), "EXPIRED", now, null, "AUTO");
                            insertOperationLog(record.getId(), null, null, "UNBAN_AUTO", "ACTIVE", "EXPIRED");
                            return record;
                        }).subscribeOn(Schedulers.boundedElastic())
                        .then(unbanTarget(record))
                        .then(refreshUserStatusIfNeeded(record)))
                .then();
    }

    public Mono<Void> syncActiveBansOnce() {
        var now = LocalDateTime.now();
        var limit = banProperties.getSweepBatchSize();
        return syncActivePage(now, limit, 0);
    }

    private Mono<Void> syncActivePage(LocalDateTime now, int limit, int offset) {
        return Mono.fromCallable(() -> banRecordMapper.selectActiveForSync(now, limit, offset))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(list -> {
                    if (list == null || list.isEmpty()) return Mono.empty();
                    return Flux.fromIterable(list)
                            .concatMap(this::applyActiveBanToRedis)
                            .then(list.size() < limit ? Mono.empty() : syncActivePage(now, limit, offset + limit));
                });
    }

    private Mono<Void> applyActiveBanToRedis(BanRecordDO record) {
        var expiresAt = record.getExpiresAt();
        Duration ttl = null;
        if (expiresAt != null) {
            var now = LocalDateTime.now();
            var seconds = java.time.Duration.between(now, expiresAt).getSeconds();
            if (seconds <= 0) return Mono.empty();
            ttl = Duration.ofSeconds(seconds);
        }
        if ("IP".equalsIgnoreCase(record.getTargetType())) {
            if (isWhitelistedIp(record.getTargetValue())) return Mono.empty();
            return banStore.banIp(record.getTargetValue(), ttl);
        }
        if ("EMAIL".equalsIgnoreCase(record.getTargetType())) {
            if (isWhitelistedEmail(record.getTargetValue())) return Mono.empty();
            return banStore.banEmail(record.getTargetValue(), ttl);
        }
        if ("USER".equalsIgnoreCase(record.getTargetType())) {
            try {
                var userId = Long.parseLong(record.getTargetValue());
                return banStore.banUser(userId, ttl);
            } catch (Exception ignored) {
                return Mono.empty();
            }
        }
        return Mono.empty();
    }

    private Mono<Void> unbanTarget(BanRecordDO record) {
        if ("IP".equalsIgnoreCase(record.getTargetType())) {
            return banStore.unbanIp(record.getTargetValue());
        }
        if ("EMAIL".equalsIgnoreCase(record.getTargetType())) {
            return banStore.unbanEmail(record.getTargetValue());
        }
        if ("USER".equalsIgnoreCase(record.getTargetType())) {
            try {
                var userId = Long.parseLong(record.getTargetValue());
                return banStore.unbanUser(userId);
            } catch (Exception ignored) {
                return Mono.empty();
            }
        }
        return Mono.empty();
    }

    private Mono<Void> refreshUserStatusIfNeeded(BanRecordDO record) {
        if (record == null || record.getBannedUserId() == null || record.getBannedUserId() <= 0) return Mono.empty();
        var userId = record.getBannedUserId();
        return Mono.fromRunnable(() -> {
                    var now = LocalDateTime.now();
                    var activeUserBans = banRecordMapper.countActiveByUserIdAndType(userId, "USER", now);
                    if (activeUserBans > 0) {
                        userMapper.updateStatus(userId, "BANNED");
                        return;
                    }

                    var activeEmailBans = banRecordMapper.countActiveByUserIdAndType(userId, "EMAIL", now);
                    if (activeEmailBans > 0) {
                        userMapper.updateStatus(userId, "SUSPENDED");
                        return;
                    }

                    userMapper.updateStatus(userId, "NORMAL");
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Void> invalidateUserTokensByEmail(String email) {
        return Mono.fromCallable(() -> userMapper.selectByEmail(email))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null || user.getId() == null) return Mono.empty();
                    return tokenStore.bumpTokenVersion(user.getId()).then(tokenStore.revokeAllRefreshTokens(user.getId())).then();
                });
    }

    private boolean isWhitelistedIp(String ip) {
        if (ip == null) return false;
        return banProperties.getWhitelistIps().stream().anyMatch(w -> w != null && w.equalsIgnoreCase(ip.trim()));
    }

    private boolean isWhitelistedEmail(String email) {
        if (email == null) return false;
        var normalized = email.trim().toLowerCase();
        return banProperties.getWhitelistEmails().stream().anyMatch(w -> w != null && w.trim().toLowerCase().equals(normalized));
    }
}
