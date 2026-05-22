package io.arknights.dateorfriends.modules.admin.permission.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminRoleOperationLogDO {
    private Long id;
    private Long actorId;
    private String actorRole;
    private Long targetUserId;
    private String actionType;
    private String fromRole;
    private String toRole;
    private LocalDateTime createdAt;
}

