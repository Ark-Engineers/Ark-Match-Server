package io.arknights.dateorfriends.modules.admin.report.controller;

import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import io.arknights.dateorfriends.modules.user.online.mapper.ReportDO;
import io.arknights.dateorfriends.modules.user.online.mapper.ReportMapper;
import io.arknights.dateorfriends.modules.user.notification.service.SiteNotificationService;
import io.arknights.dateorfriends.modules.user.profile.mapper.UserProfileDO;
import io.arknights.dateorfriends.modules.user.profile.mapper.UserProfileMapper;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.security.ban.BanService;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/admin/report")
public class AdminReportController {

    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final BanService banService;
    private final SiteNotificationService notificationService;

    public AdminReportController(ReportMapper reportMapper, UserMapper userMapper,
                                 UserProfileMapper userProfileMapper, BanService banService,
                                 SiteNotificationService notificationService) {
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.banService = banService;
        this.notificationService = notificationService;
    }

    public record ReportPageResponse(long total, int page, int size, List<ReportItem> items) {
    }

    public record ReportItem(
            ReportDO report,
            UserSummary reporter,
            UserSummary reported
    ) {
    }

    public record UserSummary(Long userId, String account, String nickname, String email, String status) {
    }

    public record ReportDetail(
            ReportDO report,
            UserSummary reporter,
            UserSummary reported,
            UserProfileDO reportedProfile
    ) {
    }

    public record HandleRequest(
            boolean resetNickname,
            boolean resetSignature,
            boolean banUser,
            boolean banIp,
            Long banDurationSeconds,
            String banReason,
            boolean dismiss
    ) {
    }

    @GetMapping("/list")
    public Mono<ApiResponse<ReportPageResponse>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "reportType", required = false) String reportType,
            @RequestParam(value = "reportedUserId", required = false) Long reportedUserId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            ServerWebExchange exchange
    ) {
        var principal = requireAdmin(exchange);
        final int safePage = Math.max(1, page);
        final int safeSize = Math.min(200, Math.max(1, size));
        final int offset = (safePage - 1) * safeSize;

        return Mono.fromCallable(() -> {
                    var total = reportMapper.count(status, reportType, reportedUserId, null, keyword);
                    var list = reportMapper.selectList(status, reportType, reportedUserId, null, keyword, safeSize, offset);
                    var items = new ArrayList<ReportItem>();
                    for (var r : list) {
                        var reporter = buildUserSummary(r.getReporterUserId());
                        var reported = buildUserSummary(r.getReportedUserId());
                        items.add(new ReportItem(r, reporter, reported));
                    }
                    return ApiResponse.ok(new ReportPageResponse(total, safePage, safeSize, items));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<ReportDetail>> detail(@PathVariable("id") long id, ServerWebExchange exchange) {
        var principal = requireAdmin(exchange);
        if (id <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));
        return Mono.fromCallable(() -> {
                    var report = reportMapper.selectById(id);
                    if (report == null) throw new BusinessException(ErrorCode.OP_FAILED, "举报记录不存在");
                    var reporter = buildUserSummary(report.getReporterUserId());
                    var reported = buildUserSummary(report.getReportedUserId());
                    var profile = userProfileMapper.selectByUserId(report.getReportedUserId());
                    return ApiResponse.ok(new ReportDetail(report, reporter, reported, profile));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{id}/handle")
    public Mono<ApiResponse<Void>> handle(@PathVariable("id") long id,
                                          @Valid @RequestBody HandleRequest req,
                                          ServerWebExchange exchange) {
        var principal = requireAdmin(exchange);
        if (id <= 0) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));

        return Mono.fromCallable(() -> reportMapper.selectById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(report -> {
                    if (report == null) return Mono.error(new BusinessException(ErrorCode.OP_FAILED, "举报记录不存在"));
                    if (!"PENDING".equals(report.getStatus())) {
                        return Mono.error(new BusinessException(ErrorCode.OP_FAILED, "该举报已处理"));
                    }

                    var actions = new ArrayList<String>();
                    var now = LocalDateTime.now();

                    var chain = Mono.<Void>empty();

                    if (req.resetNickname()) {
                        chain = chain.then(Mono.fromRunnable(() -> userMapper.updateNickname(report.getReportedUserId(), "违规昵称"))
                                .subscribeOn(Schedulers.boundedElastic()))
                                .then(Mono.fromRunnable(() -> actions.add("RESET_NICKNAME")));
                    }

                    if (req.resetSignature()) {
                        chain = chain.then(Mono.fromRunnable(() -> {
                                    var profile = userProfileMapper.selectByUserId(report.getReportedUserId());
                                    if (profile == null) {
                                        profile = new UserProfileDO();
                                        profile.setUserId(report.getReportedUserId());
                                    }
                                    profile.setSignature("违规签名");
                                    userProfileMapper.upsert(profile);
                                })
                                .subscribeOn(Schedulers.boundedElastic()))
                                .then(Mono.fromRunnable(() -> actions.add("RESET_SIGNATURE")));
                    }

                    if (req.banUser()) {
                        var reason = req.banReason() != null ? req.banReason() : "举报处理：账号封禁";
                        chain = chain.then(banService.banUser(principal.userId(), report.getReportedUserId(), reason, req.banDurationSeconds())
                                .onErrorResume(BusinessException.class, e -> {
                                    if (e.getErrorCode() == ErrorCode.ALREADY_BANNED) return Mono.empty();
                                    return Mono.error(e);
                                })
                                .then(Mono.fromRunnable(() -> actions.add("BAN_USER"))));
                    }

                    if (req.banIp()) {
                        chain = chain.then(Mono.fromCallable(() -> userMapper.selectById(report.getReportedUserId()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(user -> {
                                    if (user == null || user.getLastLoginIp() == null || user.getLastLoginIp().isBlank()) {
                                        return Mono.empty();
                                    }
                                    var reason = req.banReason() != null ? req.banReason() : "举报处理：IP封禁";
                                    return banService.banIp(principal.userId(), user.getLastLoginIp(), reason, req.banDurationSeconds())
                                            .onErrorResume(BusinessException.class, e -> {
                                                if (e.getErrorCode() == ErrorCode.ALREADY_BANNED) return Mono.empty();
                                                return Mono.error(e);
                                            });
                                })
                                .then(Mono.fromRunnable(() -> actions.add("BAN_IP"))));
                    }

                    if (req.dismiss()) {
                        actions.add("DISMISSED");
                    }

                    var newStatus = req.dismiss() && actions.size() == 1 ? "DISMISSED" : "HANDLED";
                    var actionTaken = String.join(",", actions);

                    return chain.then(Mono.fromCallable(() -> userMapper.selectById(principal.userId()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .defaultIfEmpty(null)
                                .flatMap(adminUser -> {
                                    var adminName = adminUser != null && adminUser.getNickname() != null
                                            ? adminUser.getNickname() : ("管理员#" + principal.userId());
                                    var notifyMono = Mono.<Void>empty();
                                    if (req.resetNickname()) {
                                        notifyMono = notifyMono.then(notificationService.sendToUser(
                                                principal.userId(), report.getReportedUserId(), "ACCOUNT",
                                                "昵称已被重置",
                                                "你的昵称已被管理员（" + adminName + "）重置为违规昵称，请修改后使用。",
                                                "IMPORTANT", null, null).then());
                                    }
                                    if (req.resetSignature()) {
                                        notifyMono = notifyMono.then(notificationService.sendToUser(
                                                principal.userId(), report.getReportedUserId(), "ACCOUNT",
                                                "签名已被重置",
                                                "你的签名已被管理员（" + adminName + "）重置为违规签名，请修改后使用。",
                                                "IMPORTANT", null, null).then());
                                    }
                                    return notifyMono.then(Mono.fromCallable(() -> {
                                        reportMapper.updateHandled(id, newStatus, principal.userId(), now, actionTaken, null);
                                        return ApiResponse.<Void>ok(null);
                                    }).subscribeOn(Schedulers.boundedElastic()));
                                }));
                });
    }

    private UserSummary buildUserSummary(Long userId) {
        if (userId == null || userId <= 0) return null;
        var user = userMapper.selectById(userId);
        if (user == null) return new UserSummary(userId, null, null, null, null);
        return new UserSummary(user.getId(), user.getAccount(), user.getNickname(), user.getEmail(), user.getStatus());
    }

    private JwtPrincipal requireAdmin(ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var role = String.valueOf(principal.role() == null ? "" : principal.role()).toUpperCase();
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return principal;
    }
}
