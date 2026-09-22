package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RaceRoundDO {
    private Long id;
    private Long raceId;
    private Integer roundNo;
    private String racerIds;
    private String nextLineupJson;
    private Boolean developerControlled;
    private String status;
    private Integer betDurationSeconds;
    private Integer preRaceDurationSeconds;
    private Integer raceDurationSeconds;
    private Integer podiumDurationSeconds;
    private LocalDateTime betStartAt;
    private String seed;
    private String resultCipher;
    private String resultCommit;
    private Long totalPool;
    private Integer betCount;
    private Long paidTotal;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
