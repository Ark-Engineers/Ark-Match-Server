package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RaceBetDO {
    private Long id;
    private Long roundId;
    private Long raceId;
    private Long userId;
    private Long participantId;
    private Long amount;
    private String status;
    private Long payout;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
