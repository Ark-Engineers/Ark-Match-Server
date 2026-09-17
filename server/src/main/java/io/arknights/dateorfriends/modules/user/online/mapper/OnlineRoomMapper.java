package io.arknights.dateorfriends.modules.user.online.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OnlineRoomMapper {

    @Select("""
            SELECT
              room_id AS roomId,
              name,
              online,
              permission,
              capacity,
              password_hash AS passwordHash,
              creator_user_id AS creatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt,
              deleted
            FROM `online_room`
            WHERE deleted = 0
            ORDER BY room_id ASC
            """)
    List<OnlineRoomDO> selectAll();

    @Select("""
            SELECT
              room_id AS roomId,
              name,
              online,
              permission,
              capacity,
              password_hash AS passwordHash,
              creator_user_id AS creatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt,
              deleted
            FROM `online_room`
            WHERE room_id = #{roomId}
            LIMIT 1
            """)
    OnlineRoomDO selectByRoomId(@Param("roomId") String roomId);

    @Insert("""
            INSERT INTO `online_room` (
              room_id,
              name,
              online,
              permission,
              capacity,
              password_hash,
              creator_user_id,
              created_at,
              updated_at,
              deleted
            )
            VALUES (
              #{roomId},
              #{name},
              #{online},
              #{permission},
              #{capacity},
              #{passwordHash},
              #{creatorUserId},
              #{createdAt},
              #{updatedAt},
              #{deleted}
            )
            """)
    int insert(OnlineRoomDO room);

    @Insert("""
            INSERT IGNORE INTO `online_room` (
              room_id,
              name,
              online,
              permission,
              capacity,
              password_hash,
              creator_user_id,
              created_at,
              updated_at,
              deleted
            )
            VALUES (
              #{roomId},
              #{name},
              #{online},
              #{permission},
              #{capacity},
              #{passwordHash},
              #{creatorUserId},
              #{createdAt},
              #{updatedAt},
              #{deleted}
            )
            """)
    int insertIgnore(OnlineRoomDO room);

    @Update("""
            UPDATE `online_room`
            SET online = #{online},
                updated_at = #{updatedAt}
            WHERE room_id = #{roomId}
              AND deleted = 0
            """)
    int updateOnline(@Param("roomId") String roomId, @Param("online") int online, @Param("updatedAt") LocalDateTime updatedAt);
}
