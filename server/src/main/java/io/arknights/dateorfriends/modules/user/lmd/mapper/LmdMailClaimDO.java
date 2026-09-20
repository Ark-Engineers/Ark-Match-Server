package io.arknights.dateorfriends.modules.user.lmd.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LmdMailClaimDO {
    private Long id;
    private Long notificationId;
    private Long userId;
    private Long amount;
    private String traceId;
    private String requestIp;
    private LocalDateTime createdAt;
}
