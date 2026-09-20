package io.arknights.dateorfriends.modules.user.lmd.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LmdTransactionDO {
    private Long id;
    private Long userId;
    private Long amount;
    private Long balanceAfter;
    private String type;
    private String refType;
    private Long refId;
    private String description;
    private String traceId;
    private String requestIp;
    private Long createdBy;
    private LocalDateTime createdAt;
}
