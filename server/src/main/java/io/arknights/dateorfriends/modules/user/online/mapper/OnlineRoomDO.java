package io.arknights.dateorfriends.modules.user.online.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OnlineRoomDO {
    private String roomId;
    private String name;
    private Integer online;
    private String permission;
    private Integer capacity;
    private String passwordHash;
    private Long creatorUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
