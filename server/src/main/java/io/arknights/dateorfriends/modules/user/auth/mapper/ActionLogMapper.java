package io.arknights.dateorfriends.modules.user.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActionLogMapper {

    @Insert("""
            INSERT INTO `action_log` (user_id, ip, api)
            VALUES (#{userId}, #{ip}, #{api})
            """)
    int insert(@Param("userId") long userId, @Param("ip") String ip, @Param("api") String api);

    @Select("""
            SELECT ip
            FROM `action_log`
            WHERE user_id = #{userId}
              AND ip IS NOT NULL
              AND ip <> ''
              AND LOWER(ip) <> 'unknown'
            GROUP BY ip
            ORDER BY MAX(created_at) DESC, MAX(id) DESC
            """)
    java.util.List<String> selectDistinctIpsByUserId(@Param("userId") long userId);

    @Select("""
            <script>
            SELECT DISTINCT user_id
            FROM `action_log`
            WHERE ip IN
            <foreach item="ip" collection="ips" open="(" separator="," close=")">
              #{ip}
            </foreach>
            </script>
            """)
    java.util.List<Long> selectDistinctUserIdsByIps(@Param("ips") java.util.List<String> ips);
}
