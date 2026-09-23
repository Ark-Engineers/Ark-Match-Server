package io.arknights.dateorfriends.modules.user.online.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReportDO {
    private Long id;
    private Long reporterUserId;
    private Long reportedUserId;
    private String reportType;
    private String content;
    private String roomId;
    private Long chatMessageId;
    private String status;
    private Long handledBy;
    private LocalDateTime handledAt;
    private String actionTaken;
    private String actionDetail;
    private LocalDateTime createdAt;
}
