package io.arknights.dateorfriends.modules.user.online.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OnlineRoomWhitelistDO {
    private String roomId;
    private Long userId;
    private LocalDateTime createdAt;
}
