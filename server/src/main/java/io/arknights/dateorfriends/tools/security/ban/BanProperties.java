package io.arknights.dateorfriends.tools.security.ban;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.ban")
public class BanProperties {
    private List<String> whitelistIps = new ArrayList<>(List.of("127.0.0.1", "::1"));
    private List<String> whitelistEmails = new ArrayList<>();

    private int minAdminWeight = 100;

    private long syncActiveBansFixedDelayMs = 300_000;
    private long expireSweepFixedDelayMs = 60_000;
    private int sweepBatchSize = 200;

    public List<String> getWhitelistIps() {
        return whitelistIps;
    }

    public void setWhitelistIps(List<String> whitelistIps) {
        this.whitelistIps = whitelistIps;
    }

    public List<String> getWhitelistEmails() {
        return whitelistEmails;
    }

    public void setWhitelistEmails(List<String> whitelistEmails) {
        this.whitelistEmails = whitelistEmails;
    }

    public int getMinAdminWeight() {
        return minAdminWeight;
    }

    public void setMinAdminWeight(int minAdminWeight) {
        this.minAdminWeight = minAdminWeight;
    }

    public long getSyncActiveBansFixedDelayMs() {
        return syncActiveBansFixedDelayMs;
    }

    public void setSyncActiveBansFixedDelayMs(long syncActiveBansFixedDelayMs) {
        this.syncActiveBansFixedDelayMs = syncActiveBansFixedDelayMs;
    }

    public long getExpireSweepFixedDelayMs() {
        return expireSweepFixedDelayMs;
    }

    public void setExpireSweepFixedDelayMs(long expireSweepFixedDelayMs) {
        this.expireSweepFixedDelayMs = expireSweepFixedDelayMs;
    }

    public int getSweepBatchSize() {
        return sweepBatchSize;
    }

    public void setSweepBatchSize(int sweepBatchSize) {
        this.sweepBatchSize = sweepBatchSize;
    }
}
