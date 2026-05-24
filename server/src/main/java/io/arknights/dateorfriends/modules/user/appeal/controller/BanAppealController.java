package io.arknights.dateorfriends.modules.user.appeal.controller;

import io.arknights.dateorfriends.modules.user.appeal.mapper.BanAppealDO;
import io.arknights.dateorfriends.modules.user.appeal.mapper.BanAppealMapper;
import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import io.arknights.dateorfriends.tools.web.IpUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class BanAppealController {
    private final BanAppealMapper banAppealMapper;
    private final UserMapper userMapper;

    public BanAppealController(BanAppealMapper banAppealMapper, UserMapper userMapper) {
        this.banAppealMapper = banAppealMapper;
        this.userMapper = userMapper;
    }

    public record SubmitBanAppealRequest(
            @NotBlank @Size(max = 64) String account,
            @Size(max = 128) String contact,
            @NotBlank @Size(max = 2000) String content
    ) {
    }

    public record SubmitBanAppealResponse(long id) {
    }

    @PostMapping("/appeal/ban/submit")
    public Mono<ApiResponse<SubmitBanAppealResponse>> submit(@Valid @RequestBody SubmitBanAppealRequest req, ServerWebExchange exchange) {
        var ip = IpUtils.resolveClientIp(exchange);
        var account = req.account() == null ? "" : req.account().trim();
        if (account.isBlank()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID));

        return Mono.fromCallable(() -> {
                    var user = userMapper.selectByAccount(account);
                    Long userId = null;
                    if (user != null && user.getId() != null) userId = user.getId();

                    var appeal = new BanAppealDO();
                    appeal.setAccount(account);
                    appeal.setUserId(userId);
                    appeal.setContact(req.contact() == null ? null : req.contact().trim());
                    appeal.setContent(req.content() == null ? "" : req.content().trim());
                    appeal.setStatus("PENDING");
                    appeal.setIp(ip);
                    banAppealMapper.insert(appeal);
                    return appeal.getId() == null ? 0L : appeal.getId();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(id -> ApiResponse.ok(new SubmitBanAppealResponse(id)));
    }
}

