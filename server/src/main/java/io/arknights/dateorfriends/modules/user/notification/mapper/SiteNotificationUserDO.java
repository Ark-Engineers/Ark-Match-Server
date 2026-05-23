package io.arknights.dateorfriends.modules.user.notification.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SiteNotificationUserDO {
    private Long id;
    private Long notificationId;
    private Long userId;
    private Integer read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}

