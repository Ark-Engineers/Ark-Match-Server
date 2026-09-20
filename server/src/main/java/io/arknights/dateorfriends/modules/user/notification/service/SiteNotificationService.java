package io.arknights.dateorfriends.modules.user.notification.service;

import io.arknights.dateorfriends.modules.user.notification.mapper.SiteNotificationDO;
import io.arknights.dateorfriends.modules.user.notification.mapper.SiteNotificationMapper;
import io.arknights.dateorfriends.modules.user.notification.mapper.SiteNotificationUserDO;
import io.arknights.dateorfriends.modules.user.notification.mapper.SiteNotificationUserMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class SiteNotificationService {
    private final SiteNotificationMapper notificationMapper;
    private final SiteNotificationUserMapper notificationUserMapper;

    public SiteNotificationService(SiteNotificationMapper notificationMapper, SiteNotificationUserMapper notificationUserMapper) {
        this.notificationMapper = notificationMapper;
        this.notificationUserMapper = notificationUserMapper;
    }

    public Mono<Long> sendToUser(Long actorId, long userId, String type, String title, String content, String level, String linkUrl, String payloadJson) {
        return Mono.fromCallable(() -> {
                    var now = LocalDateTime.now();
                    var n = new SiteNotificationDO();
                    n.setType(type == null ? "SYSTEM" : type.trim());
                    n.setTitle(title == null ? "" : title.trim());
                    n.setContent(content == null ? "" : content);
                    n.setLevel(level == null ? "NORMAL" : level.trim().toUpperCase());
                    n.setLinkUrl(linkUrl);
                    n.setPayloadJson(payloadJson);
                    n.setStatus("SENT");
                    n.setExpireAt(null);
                    n.setCreatedBy(actorId);
                    n.setCreatedAt(now);
                    n.setUpdatedAt(now);
                    notificationMapper.insert(n);

                    var nu = new SiteNotificationUserDO();
                    nu.setNotificationId(n.getId());
                    nu.setUserId(userId);
                    nu.setRead(0);
                    nu.setReadAt(null);
                    nu.setCreatedAt(now);
                    notificationUserMapper.insertIgnore(nu);
                    return n.getId();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public record BroadcastResult(long notificationId, long deliveredCount) {
    }

    /** 群发站内通知（带可选龙门币奖励字段），投递给全部未注销用户；龙门币金额的合法性由调用方（龙门币模块）负责 */
    public Mono<BroadcastResult> sendToAllUsers(
            Long actorId,
            String type,
            String title,
            String content,
            String level,
            String linkUrl,
            String payloadJson,
            Long lmdAmount,
            LocalDateTime lmdClaimExpireAt
    ) {
        return Mono.fromCallable(() -> {
                    var now = LocalDateTime.now();
                    var n = new SiteNotificationDO();
                    n.setType(type == null ? "SYSTEM" : type.trim());
                    n.setTitle(title == null ? "" : title.trim());
                    n.setContent(content == null ? "" : content);
                    n.setLevel(level == null ? "NORMAL" : level.trim().toUpperCase());
                    n.setLinkUrl(linkUrl);
                    n.setPayloadJson(payloadJson);
                    n.setLmdAmount(lmdAmount == null ? 0L : lmdAmount);
                    n.setLmdClaimExpireAt(lmdClaimExpireAt);
                    n.setStatus("SENT");
                    n.setExpireAt(null);
                    n.setCreatedBy(actorId);
                    n.setCreatedAt(now);
                    n.setUpdatedAt(now);
                    notificationMapper.insert(n);

                    var delivered = notificationUserMapper.broadcastToAll(n.getId(), now);
                    return new BroadcastResult(n.getId(), delivered);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}

