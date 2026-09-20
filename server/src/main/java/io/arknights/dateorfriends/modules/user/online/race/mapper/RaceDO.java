package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RaceDO {
    private Long id;
    private String roomId;
    private String name;
    private String status;
    private Integer sessionType;
    private Integer totalRounds;
    private Integer participantMode;
    private Integer betDurationSeconds;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
