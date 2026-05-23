package io.arknights.dateorfriends.modules.user.notification.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SiteNotificationDO {
    private Long id;
    private String type;
    private String title;
    private String content;
    private String level;
    private String linkUrl;
    private String payloadJson;
    private String status;
    private LocalDateTime expireAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

