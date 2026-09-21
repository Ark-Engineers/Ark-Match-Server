package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RaceSettlementDO {
    private Long id;
    private Long roundId;
    private Long userId;
    private Long assetId;
    private Integer rankNo;
    private Long betAmount;
    private Long payout;
    private LocalDateTime createdAt;
}
