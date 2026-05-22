package io.arknights.dateorfriends.modules.admin.ban.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BanRecordMapper {

    @Insert("""
            INSERT INTO `ban_record` (
              target_type,
              target_value,
              banned_user_id,
              report_id,
              admin_id,
              reason,
              duration_seconds,
              effective_at,
              expires_at,
              status
            )
            VALUES (
              #{targetType},
              #{targetValue},
              #{bannedUserId},
              #{reportId},
              #{adminId},
              #{reason},
              #{durationSeconds},
              #{effectiveAt},
              #{expiresAt},
              #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BanRecordDO record);

    @Update("""
            UPDATE `ban_record`
            SET
              status = #{status},
              unbanned_at = #{unbannedAt},
              unbanned_by = #{unbannedBy},
              unban_type = #{unbanType}
            WHERE id = #{id}
            """)
    int updateUnbanState(
            @Param("id") long id,
            @Param("status") String status,
            @Param("unbannedAt") LocalDateTime unbannedAt,
            @Param("unbannedBy") Long unbannedBy,
            @Param("unbanType") String unbanType
    );

    @Select("""
            SELECT
              id,
              target_type AS targetType,
              target_value AS targetValue,
              banned_user_id AS bannedUserId,
              report_id AS reportId,
              admin_id AS adminId,
              reason,
              duration_seconds AS durationSeconds,
              effective_at AS effectiveAt,
              expires_at AS expiresAt,
              status,
              unbanned_at AS unbannedAt,
              unbanned_by AS unbannedBy,
              unban_type AS unbanType,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `ban_record`
            WHERE id = #{id}
            LIMIT 1
            """)
    BanRecordDO selectById(@Param("id") long id);

    @Select("""
            <script>
            SELECT
              id,
              target_type AS targetType,
              target_value AS targetValue,
              banned_user_id AS bannedUserId,
              report_id AS reportId,
              admin_id AS adminId,
              reason,
              duration_seconds AS durationSeconds,
              effective_at AS effectiveAt,
              expires_at AS expiresAt,
              status,
              unbanned_at AS unbannedAt,
              unbanned_by AS unbannedBy,
              unban_type AS unbanType,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `ban_record`
            WHERE 1=1
              <if test="targetType != null and targetType != ''">
                AND target_type = #{targetType}
              </if>
              <if test="targetValue != null and targetValue != ''">
                AND target_value LIKE CONCAT('%', #{targetValue}, '%')
              </if>
              <if test="bannedUserId != null">
                AND banned_user_id = #{bannedUserId}
              </if>
              <if test="reportId != null">
                AND report_id = #{reportId}
              </if>
              <if test="keyword != null and keyword != ''">
                AND MATCH(target_value, reason) AGAINST(#{keyword} IN BOOLEAN MODE)
              </if>
              <if test="status != null and status != ''">
                AND status = #{status}
              </if>
              <if test="adminId != null">
                AND admin_id = #{adminId}
              </if>
              <if test="effectiveFrom != null">
                AND effective_at &gt;= #{effectiveFrom}
              </if>
              <if test="effectiveTo != null">
                AND effective_at &lt;= #{effectiveTo}
              </if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<BanRecordDO> selectList(
            @Param("targetType") String targetType,
            @Param("targetValue") String targetValue,
            @Param("bannedUserId") Long bannedUserId,
            @Param("reportId") Long reportId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("adminId") Long adminId,
            @Param("effectiveFrom") LocalDateTime effectiveFrom,
            @Param("effectiveTo") LocalDateTime effectiveTo,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `ban_record`
            WHERE 1=1
              <if test="targetType != null and targetType != ''">
                AND target_type = #{targetType}
              </if>
              <if test="targetValue != null and targetValue != ''">
                AND target_value LIKE CONCAT('%', #{targetValue}, '%')
              </if>
              <if test="bannedUserId != null">
                AND banned_user_id = #{bannedUserId}
              </if>
              <if test="reportId != null">
                AND report_id = #{reportId}
              </if>
              <if test="keyword != null and keyword != ''">
                AND MATCH(target_value, reason) AGAINST(#{keyword} IN BOOLEAN MODE)
              </if>
              <if test="status != null and status != ''">
                AND status = #{status}
              </if>
              <if test="adminId != null">
                AND admin_id = #{adminId}
              </if>
              <if test="effectiveFrom != null">
                AND effective_at &gt;= #{effectiveFrom}
              </if>
              <if test="effectiveTo != null">
                AND effective_at &lt;= #{effectiveTo}
              </if>
            </script>
            """)
    long count(
            @Param("targetType") String targetType,
            @Param("targetValue") String targetValue,
            @Param("bannedUserId") Long bannedUserId,
            @Param("reportId") Long reportId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("adminId") Long adminId,
            @Param("effectiveFrom") LocalDateTime effectiveFrom,
            @Param("effectiveTo") LocalDateTime effectiveTo
    );

    @Select("""
            SELECT
              id,
              target_type AS targetType,
              target_value AS targetValue,
              banned_user_id AS bannedUserId,
              report_id AS reportId,
              admin_id AS adminId,
              reason,
              duration_seconds AS durationSeconds,
              effective_at AS effectiveAt,
              expires_at AS expiresAt,
              status,
              unbanned_at AS unbannedAt,
              unbanned_by AS unbannedBy,
              unban_type AS unbanType,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `ban_record`
            WHERE status = 'ACTIVE'
              AND expires_at IS NOT NULL
              AND expires_at <= #{now}
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<BanRecordDO> selectExpiredActive(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("""
            SELECT
              id,
              target_type AS targetType,
              target_value AS targetValue,
              banned_user_id AS bannedUserId,
              report_id AS reportId,
              admin_id AS adminId,
              reason,
              duration_seconds AS durationSeconds,
              effective_at AS effectiveAt,
              expires_at AS expiresAt,
              status,
              unbanned_at AS unbannedAt,
              unbanned_by AS unbannedBy,
              unban_type AS unbanType,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `ban_record`
            WHERE status = 'ACTIVE'
              AND (expires_at IS NULL OR expires_at > #{now})
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<BanRecordDO> selectActiveForSync(@Param("now") LocalDateTime now, @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT
              id,
              target_type AS targetType,
              target_value AS targetValue,
              banned_user_id AS bannedUserId,
              report_id AS reportId,
              admin_id AS adminId,
              reason,
              duration_seconds AS durationSeconds,
              effective_at AS effectiveAt,
              expires_at AS expiresAt,
              status,
              unbanned_at AS unbannedAt,
              unbanned_by AS unbannedBy,
              unban_type AS unbanType,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `ban_record`
            WHERE banned_user_id = #{userId}
              <if test="status != null and status != ''">
                AND status = #{status}
              </if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<BanRecordDO> selectListForUser(
            @Param("userId") long userId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `ban_record`
            WHERE banned_user_id = #{userId}
              <if test="status != null and status != ''">
                AND status = #{status}
              </if>
            </script>
            """)
    long countForUser(@Param("userId") long userId, @Param("status") String status);

    @Select("""
            <script>
            SELECT
              id,
              target_type AS targetType,
              target_value AS targetValue,
              banned_user_id AS bannedUserId,
              report_id AS reportId,
              admin_id AS adminId,
              reason,
              duration_seconds AS durationSeconds,
              effective_at AS effectiveAt,
              expires_at AS expiresAt,
              status,
              unbanned_at AS unbannedAt,
              unbanned_by AS unbannedBy,
              unban_type AS unbanType,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `ban_record`
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            ORDER BY id DESC
            </script>
            """)
    List<BanRecordDO> selectListByIds(@Param("ids") List<Long> ids);
}
