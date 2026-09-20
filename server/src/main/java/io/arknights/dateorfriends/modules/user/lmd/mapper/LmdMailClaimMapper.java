package io.arknights.dateorfriends.modules.user.lmd.mapper;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LmdMailClaimMapper {

    @Insert("""
            INSERT INTO `lmd_mail_claim` (
              notification_id,
              user_id,
              amount,
              trace_id,
              request_ip,
              created_at
            )
            VALUES (
              #{notificationId},
              #{userId},
              #{amount},
              #{traceId},
              #{requestIp},
              #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LmdMailClaimDO claim);

    @Select("""
            SELECT COUNT(1)
            FROM `lmd_mail_claim`
            WHERE notification_id = #{notificationId}
              AND user_id = #{userId}
            """)
    long countByNotifAndUser(@Param("notificationId") long notificationId, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `lmd_mail_claim` c
            WHERE 1=1
              <if test="notificationId != null">
                AND c.notification_id = #{notificationId}
              </if>
              <if test="userId != null">
                AND c.user_id = #{userId}
              </if>
            </script>
            """)
    long countForAdmin(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    @Select("""
            <script>
            SELECT
              c.id AS id,
              c.notification_id AS notificationId,
              c.user_id AS userId,
              c.amount AS amount,
              c.trace_id AS traceId,
              c.request_ip AS requestIp,
              c.created_at AS createdAt,
              u.account AS account,
              u.nickname AS nickname,
              n.title AS mailTitle
            FROM `lmd_mail_claim` c
            LEFT JOIN `user` u ON u.id = c.user_id
            LEFT JOIN `site_notification` n ON n.id = c.notification_id
            WHERE 1=1
              <if test="notificationId != null">
                AND c.notification_id = #{notificationId}
              </if>
              <if test="userId != null">
                AND c.user_id = #{userId}
              </if>
            ORDER BY c.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminClaimItem> selectListForAdmin(
            @Param("notificationId") Long notificationId,
            @Param("userId") Long userId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Data
    class AdminClaimItem {
        private Long id;
        private Long notificationId;
        private Long userId;
        private Long amount;
        private String traceId;
        private String requestIp;
        private LocalDateTime createdAt;
        private String account;
        private String nickname;
        private String mailTitle;
    }
}
