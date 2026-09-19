package io.arknights.dateorfriends.modules.user.arknights.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ArknightsBindingDO {
    private Long userId;
    private Integer arknightsBound;
    private Integer arknightsIsMinor;
    private String arknightsHgId;
    private String arknightsUid;
    private String arknightsNickname;
    private String arknightsChannelName;
    private LocalDateTime arknightsBoundAt;
}
