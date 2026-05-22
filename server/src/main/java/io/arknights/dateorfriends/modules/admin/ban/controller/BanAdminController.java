package io.arknights.dateorfriends.modules.admin.ban.controller;

import io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordDO;
import io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordMapper;
import io.arknights.dateorfriends.modules.admin.ban.mapper.BanOperationLogDO;
import io.arknights.dateorfriends.modules.admin.ban.mapper.BanOperationLogMapper;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.security.ban.BanService;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/admin/ban")
public class BanAdminController {
    private final BanService banService;
    private final BanRecordMapper banRecordMapper;
    private final BanOperationLogMapper banOperationLogMapper;

    public BanAdminController(BanService banService, BanRecordMapper banRecordMapper, BanOperationLogMapper banOperationLogMapper) {
        this.banService = banService;
        this.banRecordMapper = banRecordMapper;
        this.banOperationLogMapper = banOperationLogMapper;
    }

    public record BanSingleRequest(
            @NotBlank String value,
            String reason,
            Long durationSeconds
    ) {
    }

    public record BanBatchRequest(
            List<String> values,
            String reason,
            Long durationSeconds,
            boolean confirm
    ) {
    }

    public record BanUserRequest(
            @Min(1) long userId,
            String reason,
            Long durationSeconds
    ) {
    }

    public record BanUserBatchRequest(
            List<Long> userIds,
            String reason,
            Long durationSeconds,
            boolean confirm
    ) {
    }

    public record UnbanRequest(@Min(1) long recordId) {
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    @GetMapping("/logs")
    public Mono<ApiResponse<PageResponse<BanOperationLogDO>>> listLogs(
            @RequestParam(value = "recordId", required = false) Long recordId,
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "fromTime", required = false) String fromTime,
            @RequestParam(value = "toTime", required = false) String toTime,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;

        LocalDateTime from = parseDateTime(fromTime);
        LocalDateTime to = parseDateTime(toTime);

        return banService.assertBanPermission(principal).then(Mono.fromCallable(() -> {
                    var total = banOperationLogMapper.count(recordId, actorId, actionType, from, to);
                    var items = banOperationLogMapper.selectList(recordId, actorId, actionType, from, to, safeSize, offset);
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok));
    }

    @PostMapping("/ip")
    public Mono<ApiResponse<BanRecordDO>> banIp(@Valid @RequestBody BanSingleRequest request, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return banService.assertBanPermission(principal)
                .then(banService.banIp(principal.userId(), request.value(), request.reason(), request.durationSeconds()))
                .map(ApiResponse::ok);
    }

    @PostMapping("/ip/batch")
    public Mono<ApiResponse<List<BanRecordDO>>> banIpBatch(@Valid @RequestBody BanBatchRequest request, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var values = request.values() == null ? List.<String>of() : request.values();
        return banService.assertBanPermission(principal)
                .then(banService.banIpBatch(principal.userId(), values, request.reason(), request.durationSeconds(), request.confirm()))
                .map(ApiResponse::ok);
    }

    @PostMapping("/email")
    public Mono<ApiResponse<BanRecordDO>> banEmail(@Valid @RequestBody BanSingleRequest request, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return banService.assertBanPermission(principal)
                .then(banService.banEmail(principal.userId(), request.value(), request.reason(), request.durationSeconds()))
                .map(ApiResponse::ok);
    }

    @PostMapping("/email/batch")
    public Mono<ApiResponse<List<BanRecordDO>>> banEmailBatch(@Valid @RequestBody BanBatchRequest request, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var values = request.values() == null ? List.<String>of() : request.values();
        return banService.assertBanPermission(principal)
                .then(banService.banEmailBatch(principal.userId(), values, request.reason(), request.durationSeconds(), request.confirm()))
                .map(ApiResponse::ok);
    }

    @PostMapping("/user")
    public Mono<ApiResponse<BanRecordDO>> banUser(@Valid @RequestBody BanUserRequest request, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return banService.assertBanPermission(principal)
                .then(banService.banUser(principal.userId(), request.userId(), request.reason(), request.durationSeconds()))
                .map(ApiResponse::ok);
    }

    @PostMapping("/user/batch")
    public Mono<ApiResponse<List<BanRecordDO>>> banUserBatch(@Valid @RequestBody BanUserBatchRequest request, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return banService.assertBanPermission(principal)
                .then(banService.banUserBatch(principal.userId(), request.userIds(), request.reason(), request.durationSeconds(), request.confirm()))
                .map(ApiResponse::ok);
    }

    @PostMapping("/unban")
    public Mono<ApiResponse<BanRecordDO>> unban(@Valid @RequestBody UnbanRequest request, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return banService.assertBanPermission(principal)
                .then(banService.unbanByRecordId(principal.userId(), request.recordId()))
                .map(ApiResponse::ok);
    }

    @GetMapping("/check/ip")
    public Mono<ApiResponse<Boolean>> checkIp(@RequestParam("value") String value, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return banService.assertBanPermission(principal).then(banService.checkIpAllowed(value)).thenReturn(ApiResponse.ok(false)).onErrorResume(BusinessException.class, e -> {
            if (e.getErrorCode() == ErrorCode.IP_BANNED) return Mono.just(ApiResponse.ok(true));
            return Mono.error(e);
        });
    }

    @GetMapping("/check/email")
    public Mono<ApiResponse<Boolean>> checkEmail(@RequestParam("value") String value, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        return banService.assertBanPermission(principal).then(banService.checkEmailAllowed(value)).thenReturn(ApiResponse.ok(false)).onErrorResume(BusinessException.class, e -> {
            if (e.getErrorCode() == ErrorCode.EMAIL_BANNED) return Mono.just(ApiResponse.ok(true));
            return Mono.error(e);
        });
    }

    @GetMapping("/records")
    public Mono<ApiResponse<PageResponse<BanRecordDO>>> listRecords(
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetValue", required = false) String targetValue,
            @RequestParam(value = "bannedUserId", required = false) Long bannedUserId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "adminId", required = false) Long adminId,
            @RequestParam(value = "effectiveFrom", required = false) String effectiveFrom,
            @RequestParam(value = "effectiveTo", required = false) String effectiveTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        var safePage = Math.max(1, page);
        var safeSize = Math.min(200, Math.max(1, size));
        var offset = (safePage - 1) * safeSize;

        LocalDateTime from = parseDateTime(effectiveFrom);
        LocalDateTime to = parseDateTime(effectiveTo);

        return banService.assertBanPermission(principal).then(Mono.fromCallable(() -> {
                    var total = banRecordMapper.count(targetType, targetValue, bannedUserId, keyword, status, adminId, from, to);
                    var items = banRecordMapper.selectList(targetType, targetValue, bannedUserId, keyword, status, adminId, from, to, safeSize, offset);
                    return new PageResponse<>(total, safePage, safeSize, items);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::ok));
    }

    @GetMapping("/records/{id}")
    public Mono<ApiResponse<BanRecordDO>> recordDetail(@PathVariable("id") long id, ServerWebExchange exchange) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        if (id <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        return banService.assertBanPermission(principal).then(Mono.fromCallable(() -> banRecordMapper.selectById(id)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(record -> record == null ? Mono.error(new BusinessException(ErrorCode.PARAM_INVALID)) : Mono.just(ApiResponse.ok(record)));
    }

    @GetMapping("/records/export")
    public Mono<ResponseEntity<byte[]>> exportRecords(
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetValue", required = false) String targetValue,
            @RequestParam(value = "bannedUserId", required = false) Long bannedUserId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "adminId", required = false) Long adminId,
            @RequestParam(value = "effectiveFrom", required = false) String effectiveFrom,
            @RequestParam(value = "effectiveTo", required = false) String effectiveTo,
            @RequestParam(value = "ids", required = false) String ids,
            ServerWebExchange exchange
    ) {
        var principal = exchange.<io.arknights.dateorfriends.tools.jwt.JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));
        LocalDateTime from = parseDateTime(effectiveFrom);
        LocalDateTime to = parseDateTime(effectiveTo);

        return banService.assertBanPermission(principal).then(Mono.fromCallable(() -> {
                    var idList = parseIds(ids);
                    if (idList != null && !idList.isEmpty()) {
                        return banRecordMapper.selectListByIds(idList);
                    }
                    return banRecordMapper.selectList(targetType, targetValue, bannedUserId, keyword, status, adminId, from, to, 5000, 0);
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toCsv)
                .map(csv -> csv.getBytes(StandardCharsets.UTF_8))
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ban-records.csv")
                        .contentType(new MediaType("text", "csv"))
                        .body(bytes));
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) return null;
        try {
            return java.util.Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(Long::parseLong)
                    .filter(x -> x > 0)
                    .distinct()
                    .toList();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toCsv(List<BanRecordDO> list) {
        var sb = new StringBuilder();
        sb.append("id,targetType,targetValue,bannedUserId,adminId,reason,durationSeconds,effectiveAt,expiresAt,status,unbannedAt,unbannedBy,unbanType,createdAt,updatedAt\n");
        for (var r : list) {
            sb.append(safe(r.getId()))
                    .append(',').append(safe(r.getTargetType()))
                    .append(',').append(safe(r.getTargetValue()))
                    .append(',').append(safe(r.getBannedUserId()))
                    .append(',').append(safe(r.getAdminId()))
                    .append(',').append(csvValue(r.getReason()))
                    .append(',').append(safe(r.getDurationSeconds()))
                    .append(',').append(safe(r.getEffectiveAt()))
                    .append(',').append(safe(r.getExpiresAt()))
                    .append(',').append(safe(r.getStatus()))
                    .append(',').append(safe(r.getUnbannedAt()))
                    .append(',').append(safe(r.getUnbannedBy()))
                    .append(',').append(safe(r.getUnbanType()))
                    .append(',').append(safe(r.getCreatedAt()))
                    .append(',').append(safe(r.getUpdatedAt()))
                    .append('\n');
        }
        return sb.toString();
    }

    private String safe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String csvValue(String v) {
        if (v == null) return "";
        var s = v.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }
}
