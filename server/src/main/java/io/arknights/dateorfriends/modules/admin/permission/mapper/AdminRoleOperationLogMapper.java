package io.arknights.dateorfriends.modules.admin.permission.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminRoleOperationLogMapper {

    @Insert("""
            INSERT INTO `admin_role_operation_log` (
              actor_id,
              actor_role,
              target_user_id,
              action_type,
              from_role,
              to_role,
              created_at
            )
            VALUES (
              #{actorId},
              #{actorRole},
              #{targetUserId},
              #{actionType},
              #{fromRole},
              #{toRole},
              #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminRoleOperationLogDO log);

    @Select("""
            <script>
            SELECT
              id,
              actor_id AS actorId,
              actor_role AS actorRole,
              target_user_id AS targetUserId,
              action_type AS actionType,
              from_role AS fromRole,
              to_role AS toRole,
              created_at AS createdAt
            FROM `admin_role_operation_log`
            WHERE 1=1
              <if test="targetUserId != null">
                AND target_user_id = #{targetUserId}
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
    List<AdminRoleOperationLogDO> selectList(
            @Param("targetUserId") Long targetUserId,
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
            FROM `admin_role_operation_log`
            WHERE 1=1
              <if test="targetUserId != null">
                AND target_user_id = #{targetUserId}
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
            @Param("targetUserId") Long targetUserId,
            @Param("actorId") Long actorId,
            @Param("actionType") String actionType,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );
}

