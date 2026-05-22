package io.arknights.dateorfriends.modules.admin.ban.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BanOperationLogDO {
    private Long id;
    private Long recordId;
    private Long actorId;
    private String actorRole;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private LocalDateTime createdAt;
}

