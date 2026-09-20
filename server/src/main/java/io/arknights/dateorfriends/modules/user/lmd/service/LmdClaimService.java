package io.arknights.dateorfriends.modules.user.lmd.service;

import io.arknights.dateorfriends.modules.user.lmd.mapper.LmdMailClaimMapper;
import io.arknights.dateorfriends.modules.user.notification.mapper.SiteNotificationMapper;
import io.arknights.dateorfriends.modules.user.notification.mapper.SiteNotificationUserMapper;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 龙门币邮件领取（与通知模块分离的独立领取接口）：
 * 领取凭证（ticket）一次性下发与核销 + 邮件资格校验 + 调用钱包服务入账。
 */
@Service
public class LmdClaimService {

    private static final String TICKET_KEY_PREFIX = "lmd:claim-ticket:";
    private static final Duration TICKET_TTL = Duration.ofMinutes(5);

    private final ReactiveStringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();
    private final SiteNotificationMapper notificationMapper;
    private final SiteNotificationUserMapper notificationUserMapper;
    private final LmdMailClaimMapper claimMapper;
    private final LmdWalletService walletService;
    private final LmdRateLimiter rateLimiter;

    private final int ticketUserPerMinute;
    private final int ticketIpPerMinute;
    private final int claimUserPerMinute;
    private final int claimIpPerMinute;

    public LmdClaimService(
            ReactiveStringRedisTemplate redis,
            SiteNotificationMapper notificationMapper,
            SiteNotificationUserMapper notificationUserMapper,
            LmdMailClaimMapper claimMapper,
            LmdWalletService walletService,
            LmdRateLimiter rateLimiter,
            @Value("${app.lmd.rate.ticket-user-per-minute:20}") int ticketUserPerMinute,
            @Value("${app.lmd.rate.ticket-ip-per-minute:30}") int ticketIpPerMinute,
            @Value("${app.lmd.rate.claim-user-per-minute:10}") int claimUserPerMinute,
            @Value("${app.lmd.rate.claim-ip-per-minute:20}") int claimIpPerMinute
    ) {
        this.redis = redis;
        this.notificationMapper = notificationMapper;
        this.notificationUserMapper = notificationUserMapper;
        this.claimMapper = claimMapper;
        this.walletService = walletService;
        this.rateLimiter = rateLimiter;
        this.ticketUserPerMinute = Math.max(1, ticketUserPerMinute);
        this.ticketIpPerMinute = Math.max(1, ticketIpPerMinute);
        this.claimUserPerMinute = Math.max(1, claimUserPerMinute);
        this.claimIpPerMinute = Math.max(1, claimIpPerMinute);
    }

    public record TicketResponse(String ticket, long expiresInSeconds) {
    }

    public record ClaimResponse(long amount, long balance) {
    }

    public record MailInfo(long notificationId, String title, long lmdAmount, LocalDateTime lmdClaimExpireAt) {
    }

    public Mono<TicketResponse> issueTicket(long userId, long notificationId, String ip) {
        return rateLimiter.check("ticket:u", String.valueOf(userId), ticketUserPerMinute)
                .then(rateLimiter.check("ticket:ip", ip, ticketIpPerMinute))
                .then(Mono.fromCallable(() -> {
                            loadAndValidateMail(userId, notificationId);
                            return randomTicket();
                        })
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(ticket -> redis.opsForValue()
                        .set(TICKET_KEY_PREFIX + ticket, userId + ":" + notificationId, TICKET_TTL)
                        .thenReturn(new TicketResponse(ticket, TICKET_TTL.toSeconds())));
    }

    public Mono<ClaimResponse> claim(long userId, long notificationId, String ticket, String traceId, String ip) {
        return rateLimiter.check("claim:u", String.valueOf(userId), claimUserPerMinute)
                .then(rateLimiter.check("claim:ip", ip, claimIpPerMinute))
                .then(consumeTicket(userId, notificationId, ticket))
                .then(Mono.fromCallable(() -> {
                            var mail = loadAndValidateMail(userId, notificationId);
                            var result = walletService.claimMailReward(userId, notificationId, mail.lmdAmount(), traceId, ip);
                            return new ClaimResponse(result.amount(), result.balanceAfter());
                        })
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private Mono<Void> consumeTicket(long userId, long notificationId, String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Mono.error(new BusinessException(ErrorCode.LMD_TICKET_INVALID));
        }
        return redis.opsForValue()
                .getAndDelete(TICKET_KEY_PREFIX + ticket)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.LMD_TICKET_INVALID)))
                .flatMap(value -> {
                    if (!(userId + ":" + notificationId).equals(value)) {
                        return Mono.error(new BusinessException(ErrorCode.LMD_TICKET_INVALID));
                    }
                    return Mono.empty();
                });
    }

    private MailInfo loadAndValidateMail(long userId, long notificationId) {
        var n = notificationMapper.selectById(notificationId);
        if (n == null || !"SENT".equals(n.getStatus())) {
            throw new BusinessException(ErrorCode.LMD_MAIL_NOT_FOUND);
        }
        var lmdAmount = n.getLmdAmount() == null ? 0L : n.getLmdAmount();
        if (lmdAmount <= 0) {
            throw new BusinessException(ErrorCode.LMD_MAIL_NO_AMOUNT);
        }
        var delivery = notificationUserMapper.selectByNotifAndUser(userId, notificationId);
        if (delivery == null) {
            throw new BusinessException(ErrorCode.LMD_MAIL_NOT_FOUND);
        }
        var expireAt = n.getLmdClaimExpireAt();
        if (expireAt != null && expireAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.LMD_MAIL_EXPIRED);
        }
        var claimed = (delivery.getClaimed() != null && delivery.getClaimed() == 1)
                || claimMapper.countByNotifAndUser(notificationId, userId) > 0;
        if (claimed) {
            throw new BusinessException(ErrorCode.LMD_ALREADY_CLAIMED);
        }
        return new MailInfo(notificationId, n.getTitle(), lmdAmount, expireAt);
    }

    private String randomTicket() {
        var bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
