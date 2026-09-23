package io.arknights.dateorfriends.modules.user.online.controller;

import io.arknights.dateorfriends.modules.user.online.mapper.ReportDO;
import io.arknights.dateorfriends.modules.user.online.mapper.ReportMapper;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import io.arknights.dateorfriends.tools.security.AuthWebFilter;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/user/report")
public class UserReportController {

    private final ReportMapper reportMapper;

    public UserReportController(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    public record SubmitReportRequest(
            @NotNull Long reportedUserId,
            @NotBlank String reportType,
            @NotBlank @Size(max = 500) String content,
            String roomId,
            Long chatMessageId
    ) {
    }

    @PostMapping
    public Mono<ApiResponse<Void>> submit(@Valid @RequestBody SubmitReportRequest req, ServerWebExchange exchange) {
        var principal = exchange.<JwtPrincipal>getAttribute(AuthWebFilter.ATTR_PRINCIPAL);
        if (principal == null) return Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED));

        var type = req.reportType().trim().toUpperCase();
        if (!"NICKNAME".equals(type) && !"SIGNATURE".equals(type) && !"CHAT".equals(type)) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "举报类型不合法"));
        }
        if (req.reportedUserId() <= 0) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "被举报人ID不合法"));
        }
        if (req.reportedUserId() == principal.userId()) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "不可举报自己"));
        }

        var report = new ReportDO();
        report.setReporterUserId(principal.userId());
        report.setReportedUserId(req.reportedUserId());
        report.setReportType(type);
        report.setContent(req.content().trim());
        report.setRoomId(req.roomId());
        report.setChatMessageId(req.chatMessageId());
        report.setStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now());

        return Mono.fromCallable(() -> {
                    reportMapper.insert(report);
                    return ApiResponse.<Void>ok(null);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
