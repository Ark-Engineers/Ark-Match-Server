package io.arknights.dateorfriends.modules.user.ban.controller;

import io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordMapper;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/user/ban")
public class UserBanController {
    private final BanRecordMapper banRecordMapper;

    public UserBanController(BanRecordMapper banRecordMapper) {
        this.banRecordMapper = banRecordMapper;
    }

    public record UserBanRecordResponse(
            long id,
            String targetType,
            String targetValue,
            long reportId,
            String reason,
            Long durationSeconds,
            java.time.LocalDateTime effectiveAt,
            java.time.LocalDateTime expiresAt,
            String status,
            java.time.LocalDateTime unbannedAt,
            String unbanType,
            java.time.LocalDateTime createdAt
    ) {
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    @GetMapping("/records")
    public Mono<ApiResponse<PageResponse<UserBanRecordResponse>>> listMyRecords(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var safePage = Math.max(1, page);
        var safeSize = Math.min(100, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;

        return Mono.fromCallable(() -> {
                    var total = banRecordMapper.countForUser(principal.userId(), status);
                    var items = banRecordMapper.selectListForUser(principal.userId(), status, safeSize, offset).stream()
                            .map(r -> new UserBanRecordResponse(
                                    r.getId(),
                                    r.getTargetType(),
                                    r.getTargetValue(),
                                    r.getReportId(),
                                    r.getReason(),
                                    r.getDurationSeconds(),
                                    r.getEffectiveAt(),
                                    r.getExpiresAt(),
                                    r.getStatus(),
                                    r.getUnbannedAt(),
                                    r.getUnbanType(),
                                    r.getCreatedAt()
                            ))
                            .toList();
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok);
    }
}

