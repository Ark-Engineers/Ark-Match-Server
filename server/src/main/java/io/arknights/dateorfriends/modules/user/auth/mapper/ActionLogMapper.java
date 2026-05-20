package io.arknights.dateorfriends.modules.user.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActionLogMapper {

    @Insert("""
            INSERT INTO `action_log` (user_id, ip, api)
            VALUES (#{userId}, #{ip}, #{api})
            """)
    int insert(@Param("userId") long userId, @Param("ip") String ip, @Param("api") String api);
}

