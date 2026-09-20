package io.arknights.dateorfriends.modules.user.online.race.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 赛马轮次调度：每秒推进一次状态机（竞猜关闭 -> 开赛 -> 结算 -> 领奖台 -> 下一轮/关闭） */
@Component
@ConditionalOnProperty(name = "app.race.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class RaceScheduler {

    private static final Logger log = LoggerFactory.getLogger(RaceScheduler.class);

    private final RaceEngineService engine;

    public RaceScheduler(RaceEngineService engine) {
        this.engine = engine;
    }

    @Scheduled(fixedDelayString = "${app.race.tick-fixed-delay-ms:1000}")
    public void tick() {
        try {
            engine.tickOnce();
        } catch (Exception e) {
            log.error("race scheduler tick failed", e);
        }
    }
}
