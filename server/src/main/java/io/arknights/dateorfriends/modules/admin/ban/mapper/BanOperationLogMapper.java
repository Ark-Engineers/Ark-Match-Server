package io.arknights.dateorfriends.modules.admin.ban.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BanOperationLogMapper {

    @Insert("""
            INSERT INTO `ban_operation_log` (
              record_id,
              actor_id,
              actor_role,
              action_type,
              from_status,
              to_status,
              created_at
            )
            VALUES (
              #{recordId},
              #{actorId},
              #{actorRole},
              #{actionType},
              #{fromStatus},
              #{toStatus},
              #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BanOperationLogDO log);

    @Select("""
            <script>
            SELECT
              id,
              record_id AS recordId,
              actor_id AS actorId,
              actor_role AS actorRole,
              action_type AS actionType,
              from_status AS fromStatus,
              to_status AS toStatus,
              created_at AS createdAt
            FROM `ban_operation_log`
            WHERE 1=1
              <if test="recordId != null">
                AND record_id = #{recordId}
              </if>
              <if test="actorId != null">
                AND actor_id = #{actorId}
              </if>
              <if test="actionType != null and actionType != ''">
                AND action_type = #{actionType}
              </if>
              <if test="fromTime != null">
                AND created_at &gt;= #{fromTime}
              </if>
              <if test="toTime != null">
                AND created_at &lt;= #{toTime}
              </if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<BanOperationLogDO> selectList(
            @Param("recordId") Long recordId,
            @Param("actorId") Long actorId,
            @Param("actionType") String actionType,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `ban_operation_log`
            WHERE 1=1
              <if test="recordId != null">
                AND record_id = #{recordId}
              </if>
              <if test="actorId != null">
                AND actor_id = #{actorId}
              </if>
              <if test="actionType != null and actionType != ''">
                AND action_type = #{actionType}
              </if>
              <if test="fromTime != null">
                AND created_at &gt;= #{fromTime}
              </if>
              <if test="toTime != null">
                AND created_at &lt;= #{toTime}
              </if>
            </script>
            """)
    long count(
            @Param("recordId") Long recordId,
            @Param("actorId") Long actorId,
            @Param("actionType") String actionType,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );
}
