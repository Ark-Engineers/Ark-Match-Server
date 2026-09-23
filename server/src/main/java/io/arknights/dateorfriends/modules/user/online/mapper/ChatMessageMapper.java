package io.arknights.dateorfriends.modules.user.online.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMessageMapper {

    @Insert("""
            INSERT INTO `chat_message` (room_id, user_id, nickname, content, sender_ip, created_at)
            VALUES (#{roomId}, #{userId}, #{nickname}, #{content}, #{senderIp}, #{createdAt})
            """)
    int insert(ChatMessageDO msg);
}
