package io.arknights.dateorfriends.modules.admin.notice.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NoticeOperationLogMapper {

    @Insert("""
            INSERT INTO `notice_operation_log` (
              notice_id,
              actor_id,
              actor_role,
              action_type,
              ip,
              detail,
              created_at
            )
            VALUES (
              #{noticeId},
              #{actorId},
              #{actorRole},
              #{actionType},
              #{ip},
              #{detail},
              #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NoticeOperationLogDO log);

    @Select("""
            <script>
            SELECT
              COUNT(1)
            FROM `notice_operation_log`
            WHERE 1=1
              <if test="noticeId != null">
                AND notice_id = #{noticeId}
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
            @Param("noticeId") Long noticeId,
            @Param("actorId") Long actorId,
            @Param("actionType") String actionType,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    @Select("""
            <script>
            SELECT
              id,
              notice_id AS noticeId,
              actor_id AS actorId,
              actor_role AS actorRole,
              action_type AS actionType,
              ip,
              detail,
              created_at AS createdAt
            FROM `notice_operation_log`
            WHERE 1=1
              <if test="noticeId != null">
                AND notice_id = #{noticeId}
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
    List<NoticeOperationLogDO> selectList(
            @Param("noticeId") Long noticeId,
            @Param("actorId") Long actorId,
            @Param("actionType") String actionType,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}

