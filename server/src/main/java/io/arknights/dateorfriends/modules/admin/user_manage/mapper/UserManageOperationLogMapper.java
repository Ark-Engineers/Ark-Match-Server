package io.arknights.dateorfriends.modules.admin.user_manage.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserManageOperationLogMapper {

    @Insert("""
            INSERT INTO `user_manage_operation_log` (
              actor_id,
              actor_role,
              target_user_id,
              action_type,
              ip,
              detail,
              diff_json,
              created_at
            )
            VALUES (
              #{actorId},
              #{actorRole},
              #{targetUserId},
              #{actionType},
              #{ip},
              #{detail},
              #{diffJson},
              #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserManageOperationLogDO log);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `user_manage_operation_log`
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
              <if test="keyword != null and keyword != ''">
                AND detail LIKE CONCAT('%', #{keyword}, '%')
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
            @Param("keyword") String keyword,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    @Select("""
            <script>
            SELECT
              id,
              actor_id AS actorId,
              actor_role AS actorRole,
              target_user_id AS targetUserId,
              action_type AS actionType,
              ip,
              detail,
              diff_json AS diffJson,
              created_at AS createdAt
            FROM `user_manage_operation_log`
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
              <if test="keyword != null and keyword != ''">
                AND detail LIKE CONCAT('%', #{keyword}, '%')
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
    List<UserManageOperationLogDO> selectList(
            @Param("targetUserId") Long targetUserId,
            @Param("actorId") Long actorId,
            @Param("actionType") String actionType,
            @Param("keyword") String keyword,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT
              id,
              actor_id AS actorId,
              actor_role AS actorRole,
              target_user_id AS targetUserId,
              action_type AS actionType,
              ip,
              detail,
              diff_json AS diffJson,
              created_at AS createdAt
            FROM `user_manage_operation_log`
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            ORDER BY id DESC
            </script>
            """)
    List<UserManageOperationLogDO> selectListByIds(@Param("ids") List<Long> ids);
}

