package io.arknights.dateorfriends.modules.user.online.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OnlineRoomWhitelistMapper {

    @Select("""
            SELECT
              room_id AS roomId,
              user_id AS userId,
              created_at AS createdAt
            FROM `online_room_whitelist`
            ORDER BY room_id ASC, user_id ASC
            """)
    List<OnlineRoomWhitelistDO> selectAll();

    @Delete("""
            DELETE FROM `online_room_whitelist`
            WHERE room_id = #{roomId}
            """)
    int deleteByRoomId(@Param("roomId") String roomId);

    @Insert("""
            <script>
            INSERT INTO `online_room_whitelist` (
              room_id,
              user_id,
              created_at
            )
            VALUES
            <foreach collection="items" item="i" separator=",">
              (
                #{i.roomId},
                #{i.userId},
                #{i.createdAt}
              )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<OnlineRoomWhitelistDO> items);
}
