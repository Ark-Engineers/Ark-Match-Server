package io.arknights.dateorfriends.modules.admin.user_manage.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserManageOperationLogDO {
    private Long id;
    private Long actorId;
    private String actorRole;
    private Long targetUserId;
    private String actionType;
    private String ip;
    private String detail;
    private String diffJson;
    private LocalDateTime createdAt;
}

