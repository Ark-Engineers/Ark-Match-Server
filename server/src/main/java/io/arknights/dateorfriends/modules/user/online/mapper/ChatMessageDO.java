package io.arknights.dateorfriends.modules.user.online.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatMessageDO {
    private Long id;
    private String roomId;
    private Long userId;
    private String nickname;
    private String content;
    private String senderIp;
    private LocalDateTime createdAt;
}
