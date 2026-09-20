package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RaceRoundDO {
    private Long id;
    private Long raceId;
    private Integer roundNo;
    private String status;
    private LocalDateTime betStartAt;
    private LocalDateTime betEndAt;
    private LocalDateTime raceStartAt;
    private LocalDateTime podiumEndAt;
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
