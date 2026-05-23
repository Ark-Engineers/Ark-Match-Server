package io.arknights.dateorfriends.modules.user.notice.controller;

import io.arknights.dateorfriends.modules.admin.notice.service.NoticeService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/user/notices")
public class UserNoticeController {
    private final NoticeService noticeService;

    public UserNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public record MarkReadRequest(@Min(1) long noticeId) {
    }

    @GetMapping
    public Mono<ApiResponse<PageResponse<NoticeService.UserNoticeItem>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));

        return noticeService.listUserItems(keyword, principal.userId(), page, size)
                .map(r -> new PageResponse<>(r.total(), r.page(), r.size(), r.items()))
                .map(ApiResponse::ok);
    }

    @GetMapping("/popup")
    public Mono<ApiResponse<NoticeService.UserNoticeItem>> popup(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));

        return noticeService.popupImportant(principal.userId())
                .map(n -> {
                    if (n == null) return null;
                    return new NoticeService.UserNoticeItem(
                            n.getId() == null ? 0 : n.getId(),
                            n.getTitle(),
                            n.getContent(),
                            n.getLevel(),
                            n.getPinned() != null && n.getPinned() == 1,
                            n.getPublishAt(),
                            n.getExpireAt(),
                            false
                    );
                })
                .map(ApiResponse::ok);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<NoticeService.UserNoticeItem>> detail(@PathVariable("id") long id, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        if (id <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));

        return noticeService.getUserDetail(id)
                .map(n -> new NoticeService.UserNoticeItem(
                        n.getId() == null ? 0 : n.getId(),
                        n.getTitle(),
                        n.getContent(),
                        n.getLevel(),
                        n.getPinned() != null && n.getPinned() == 1,
                        n.getPublishAt(),
                        n.getExpireAt(),
                        true
                ))
                .flatMap(item -> noticeService.markRead(id, principal.userId()).thenReturn(ApiResponse.ok(item)));
    }

    @PostMapping("/read")
    public Mono<ApiResponse<Void>> markRead(@Valid @RequestBody MarkReadRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return noticeService.markRead(req.noticeId(), principal.userId()).thenReturn(ApiResponse.ok(null));
    }
}

