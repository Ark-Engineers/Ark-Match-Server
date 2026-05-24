package io.arknights.dateorfriends.tools.security.ban;

import java.time.LocalDateTime;

public record BanBlockInfo(
        String reason,
        LocalDateTime effectiveAt,
        LocalDateTime expiresAt,
        Long remainingSeconds,
        Integer remainingDays
) {
}

