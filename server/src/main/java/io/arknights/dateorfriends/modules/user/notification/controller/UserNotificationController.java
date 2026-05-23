package io.arknights.dateorfriends.modules.user.notification.controller;

import io.arknights.dateorfriends.modules.user.notification.mapper.SiteNotificationUserMapper;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/user/notifications")
public class UserNotificationController {
    private final SiteNotificationUserMapper notificationUserMapper;

    public UserNotificationController(SiteNotificationUserMapper notificationUserMapper) {
        this.notificationUserMapper = notificationUserMapper;
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public record InboxItem(
            long id,
            long notificationId,
            String type,
            String title,
            String content,
            String level,
            String linkUrl,
            String payloadJson,
            LocalDateTime expireAt,
            LocalDateTime notificationCreatedAt,
            boolean read,
            LocalDateTime readAt,
            LocalDateTime deliveredAt
    ) {
    }

    public record MarkReadRequest(@Min(1) long notificationId) {
    }

    @GetMapping
    public Mono<ApiResponse<PageResponse<InboxItem>>> list(
            @RequestParam(value = "read", required = false) Integer read,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));

        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;

        final Integer safeRead = read == null ? null : (read == 1 ? 1 : 0);

        return Mono.fromCallable(() -> {
                    var total = notificationUserMapper.countInbox(principal.userId(), safeRead);
                    var list = notificationUserMapper.selectInbox(principal.userId(), safeRead, safeSize, offset);
                    var items = list.stream()
                            .map(i -> new InboxItem(
                                    i.getId() == null ? 0 : i.getId(),
                                    i.getNotificationId() == null ? 0 : i.getNotificationId(),
                                    i.getType(),
                                    i.getTitle(),
                                    i.getContent(),
                                    i.getLevel(),
                                    i.getLinkUrl(),
                                    i.getPayloadJson(),
                                    i.getExpireAt(),
                                    i.getNotificationCreatedAt(),
                                    i.getRead() != null && i.getRead() == 1,
                                    i.getReadAt(),
                                    i.getCreatedAt()
                            ))
                            .toList();
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @GetMapping("/unread-count")
    public Mono<ApiResponse<Long>> unreadCount(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));

        return Mono.fromCallable(() -> notificationUserMapper.countUnread(principal.userId()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }

    @PostMapping("/read")
    public Mono<ApiResponse<Void>> markRead(@Valid @RequestBody MarkReadRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));

        return Mono.fromRunnable(() -> notificationUserMapper.markRead(principal.userId(), req.notificationId()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ApiResponse.ok(null));
    }
}
