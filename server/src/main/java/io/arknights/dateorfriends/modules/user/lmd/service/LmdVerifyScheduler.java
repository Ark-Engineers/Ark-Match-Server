package io.arknights.dateorfriends.modules.user.lmd.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 龙门币账面校验定时任务：每日自动对账，发现不一致只告警不自动修复 */
@Component
public class LmdVerifyScheduler {

    private static final Logger log = LoggerFactory.getLogger(LmdVerifyScheduler.class);

    private final LmdVerifyService verifyService;

    public LmdVerifyScheduler(LmdVerifyService verifyService) {
        this.verifyService = verifyService;
    }

    @Scheduled(cron = "${app.lmd.verify-cron:0 30 4 * * ?}")
    public void scheduledVerify() {
        var result = verifyService.verifyAll().block(Duration.ofSeconds(60));
        if (result == null) {
            log.warn("[lmd-verify] 定时账面校验执行失败（超时）");
            return;
        }
        if (result.mismatches().isEmpty()) {
            log.info("[lmd-verify] 定时账面校验通过，共检查 {} 个龙门币账户", result.totalAccounts());
        } else {
            var detail = result.mismatches().stream()
                    .map(m -> m.userId() + "(差" + m.diff() + ")")
                    .toList();
            log.warn("[lmd-verify] 发现 {} 个账户账面不一致: {}", result.mismatches().size(), detail);
        }
    }
}
