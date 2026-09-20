package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RaceParticipantDO {
    private Long id;
    private Long raceId;
    private Integer sortNo;
    private Long spineAssetId;
    private String assetKey;
    private String name;
    private Integer type;
    private LocalDateTime createdAt;
}
