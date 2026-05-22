package io.arknights.dateorfriends.modules.admin.ban.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BanRecordDO {
    private Long id;
    private String targetType;
    private String targetValue;
    private Long bannedUserId;
    private Long reportId;
    private Long adminId;
    private String reason;
    private Long durationSeconds;
    private LocalDateTime effectiveAt;
    private LocalDateTime expiresAt;
    private String status;
    private LocalDateTime unbannedAt;
    private Long unbannedBy;
    private String unbanType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
