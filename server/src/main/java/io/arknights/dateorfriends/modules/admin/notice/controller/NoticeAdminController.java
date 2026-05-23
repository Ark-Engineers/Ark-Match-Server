package io.arknights.dateorfriends.modules.admin.notice.controller;

import io.arknights.dateorfriends.modules.admin.notice.mapper.NoticeDO;
import io.arknights.dateorfriends.modules.admin.notice.mapper.NoticeOperationLogDO;
import io.arknights.dateorfriends.modules.admin.notice.service.NoticeService;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/admin/notice")
public class NoticeAdminController {
    private final NoticeService noticeService;

    public NoticeAdminController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public record NoticeItem(
            long id,
            String title,
            String content,
            String level,
            String status,
            boolean pinned,
            LocalDateTime publishAt,
            LocalDateTime expireAt,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CreateRequest(
            String title,
            String content,
            String level,
            boolean pinned,
            String expireAt
    ) {
    }

    public record UpdateRequest(
            @Min(1) long id,
            String title,
            String content,
            String level,
            boolean pinned,
            String expireAt
    ) {
    }

    public record IdRequest(@Min(1) long id) {
    }

    @PostMapping("/create")
    public Mono<ApiResponse<NoticeItem>> create(@Valid @RequestBody CreateRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = IpUtils.resolveClientIp(exchange);
        var expireAt = parseDateTime(req.expireAt());
        return noticeService.create(principal, ip, req.title(), req.content(), req.level(), req.pinned(), expireAt)
                .map(this::toItem)
                .map(ApiResponse::ok);
    }

    @PostMapping("/update")
    public Mono<ApiResponse<NoticeItem>> update(@Valid @RequestBody UpdateRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = IpUtils.resolveClientIp(exchange);
        var expireAt = parseDateTime(req.expireAt());
        return noticeService.update(principal, ip, req.id(), req.title(), req.content(), req.level(), req.pinned(), expireAt)
                .map(this::toItem)
                .map(ApiResponse::ok);
    }

    @PostMapping("/publish")
    public Mono<ApiResponse<NoticeItem>> publish(@Valid @RequestBody IdRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = IpUtils.resolveClientIp(exchange);
        return noticeService.publish(principal, ip, req.id())
                .map(this::toItem)
                .map(ApiResponse::ok);
    }

    @PostMapping("/offline")
    public Mono<ApiResponse<NoticeItem>> offline(@Valid @RequestBody IdRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = IpUtils.resolveClientIp(exchange);
        return noticeService.offline(principal, ip, req.id())
                .map(this::toItem)
                .map(ApiResponse::ok);
    }

    @PostMapping("/delete")
    public Mono<ApiResponse<Void>> delete(@Valid @RequestBody IdRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var ip = IpUtils.resolveClientIp(exchange);
        return noticeService.delete(principal, ip, req.id())
                .thenReturn(ApiResponse.ok(null));
    }

    @GetMapping("/list")
    public Mono<ApiResponse<PageResponse<NoticeItem>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "pinned", required = false) Integer pinned,
            @RequestParam(value = "publishFrom", required = false) String publishFrom,
            @RequestParam(value = "publishTo", required = false) String publishTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var from = parseDateTime(publishFrom);
        var to = parseDateTime(publishTo);
        return noticeService.listAdmin(keyword, status, level, pinned, from, to, page, size)
                .map(r -> new PageResponse<>(
                        r.total(),
                        r.page(),
                        r.size(),
                        r.items().stream().map(this::toItem).toList()
                ))
                .map(ApiResponse::ok);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<NoticeItem>> detail(@PathVariable("id") long id, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        if (id <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        return noticeService.getAdmin(id)
                .flatMap(n -> n == null ? Mono.error(new BusinessException(ErrorCode.NOTICE_NOT_FOUND)) : Mono.just(ApiResponse.ok(toItem(n))));
    }

    @GetMapping("/logs")
    public Mono<ApiResponse<PageResponse<NoticeOperationLogDO>>> logs(
            @RequestParam(value = "noticeId", required = false) Long noticeId,
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "fromTime", required = false) String fromTime,
            @RequestParam(value = "toTime", required = false) String toTime,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var from = parseDateTime(fromTime);
        var to = parseDateTime(toTime);
        return noticeService.listLogs(noticeId, actorId, actionType, from, to, page, size)
                .map(r -> new PageResponse<>(r.total(), r.page(), r.size(), r.items()))
                .map(ApiResponse::ok);
    }

    @GetMapping("/logs/export")
    public Mono<ResponseEntity<byte[]>> exportLogs(
            @RequestParam(value = "noticeId", required = false) Long noticeId,
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "fromTime", required = false) String fromTime,
            @RequestParam(value = "toTime", required = false) String toTime,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var from = parseDateTime(fromTime);
        var to = parseDateTime(toTime);
        return noticeService.listLogs(noticeId, actorId, actionType, from, to, 1, 5000)
                .map(r -> toCsv(r.items()))
                .map(csv -> csv.getBytes(StandardCharsets.UTF_8))
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=notice-logs.csv")
                        .contentType(new MediaType("text", "csv"))
                        .body(bytes));
    }

    private NoticeItem toItem(NoticeDO n) {
        return new NoticeItem(
                n.getId() == null ? 0 : n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getLevel(),
                n.getStatus(),
                n.getPinned() != null && n.getPinned() == 1,
                n.getPublishAt(),
                n.getExpireAt(),
                n.getCreatedBy() == null ? 0 : n.getCreatedBy(),
                n.getUpdatedBy() == null ? 0 : n.getUpdatedBy(),
                n.getCreatedAt(),
                n.getUpdatedAt()
        );
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        var v = value.trim();
        try {
            return LocalDateTime.parse(v, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(v.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toCsv(List<NoticeOperationLogDO> list) {
        var sb = new StringBuilder();
        sb.append("id,noticeId,actorId,actorRole,actionType,ip,detail,createdAt\n");
        for (var r : list) {
            sb.append(safe(r.getId()))
                    .append(',').append(safe(r.getNoticeId()))
                    .append(',').append(safe(r.getActorId()))
                    .append(',').append(csvValue(r.getActorRole()))
                    .append(',').append(csvValue(r.getActionType()))
                    .append(',').append(csvValue(r.getIp()))
                    .append(',').append(csvValue(r.getDetail()))
                    .append(',').append(csvValue(r.getCreatedAt()))
                    .append('\n');
        }
        return sb.toString();
    }

    private String safe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String csvValue(Object v) {
        if (v == null) return "";
        var s = String.valueOf(v);
        var escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
