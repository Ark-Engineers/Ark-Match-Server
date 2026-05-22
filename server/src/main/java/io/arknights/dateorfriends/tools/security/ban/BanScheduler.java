package io.arknights.dateorfriends.tools.security.ban;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BanScheduler {
    private final BanService banService;

    public BanScheduler(BanService banService) {
        this.banService = banService;
    }

    @Scheduled(fixedDelayString = "${app.security.ban.expire-sweep-fixed-delay-ms:60000}")
    public void sweepExpired() {
        banService.expireSweepOnce().subscribe();
    }

    @Scheduled(fixedDelayString = "${app.security.ban.sync-active-bans-fixed-delay-ms:300000}")
    public void syncActive() {
        banService.syncActiveBansOnce().subscribe();
    }
}

