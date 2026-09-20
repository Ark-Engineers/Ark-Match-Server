package io.arknights.dateorfriends.modules.user.notification.mapper;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SiteNotificationUserMapper {

    @Insert("""
            INSERT IGNORE INTO `site_notification_user` (
              notification_id,
              user_id,
              `read`,
              read_at,
              claimed,
              claimed_at,
              created_at
            )
            VALUES (
              #{notificationId},
              #{userId},
              #{read},
              #{readAt},
              #{claimed},
              #{claimedAt},
              #{createdAt}
            )
            """)
    int insertIgnore(SiteNotificationUserDO notificationUser);

    @Insert("""
            INSERT IGNORE INTO `site_notification_user` (
              notification_id,
              user_id,
              `read`,
              read_at,
              claimed,
              claimed_at,
              created_at
            )
            SELECT
              #{notificationId},
              id,
              0,
              NULL,
              0,
              NULL,
              #{now}
            FROM `user`
            WHERE deleted = 0
            """)
    int broadcastToAll(@Param("notificationId") long notificationId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE `site_notification_user`
            SET
              `read` = 1,
              read_at = NOW()
            WHERE user_id = #{userId}
              AND notification_id = #{notificationId}
              AND `read` = 0
            """)
    int markRead(@Param("userId") long userId, @Param("notificationId") long notificationId);

    @Update("""
            UPDATE `site_notification_user`
            SET
              claimed = 1,
              claimed_at = #{claimedAt}
            WHERE user_id = #{userId}
              AND notification_id = #{notificationId}
              AND claimed = 0
            """)
    int markClaimed(
            @Param("userId") long userId,
            @Param("notificationId") long notificationId,
            @Param("claimedAt") LocalDateTime claimedAt
    );

    @Select("""
            SELECT
              nu.id AS id,
              nu.notification_id AS notificationId,
              nu.user_id AS userId,
              nu.`read` AS `read`,
              nu.read_at AS readAt,
              nu.claimed AS claimed,
              nu.claimed_at AS claimedAt,
              nu.created_at AS createdAt
            FROM `site_notification_user` nu
            WHERE nu.user_id = #{userId}
              AND nu.notification_id = #{notificationId}
            LIMIT 1
            """)
    UserInboxItem selectByNotifAndUser(
            @Param("userId") long userId,
            @Param("notificationId") long notificationId
    );

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `site_notification_user` nu
            INNER JOIN `site_notification` n ON n.id = nu.notification_id
            WHERE nu.user_id = #{userId}
              AND nu.`read` = 0
              AND n.status = 'SENT'
              AND (n.expire_at IS NULL OR n.expire_at &gt; NOW())
            </script>
            """)
    long countUnread(@Param("userId") long userId);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `site_notification_user` nu
            INNER JOIN `site_notification` n ON n.id = nu.notification_id
            WHERE nu.user_id = #{userId}
              AND n.status = 'SENT'
              AND (n.expire_at IS NULL OR n.expire_at &gt; NOW())
              <if test="read != null">
                AND nu.`read` = #{read}
              </if>
            </script>
            """)
    long countInbox(@Param("userId") long userId, @Param("read") Integer read);

    @Select("""
            <script>
            SELECT
              nu.id AS id,
              nu.notification_id AS notificationId,
              nu.user_id AS userId,
              nu.`read` AS `read`,
              nu.read_at AS readAt,
              nu.claimed AS claimed,
              nu.claimed_at AS claimedAt,
              nu.created_at AS createdAt,
              n.type AS type,
              n.title AS title,
              n.content AS content,
              n.level AS level,
              n.link_url AS linkUrl,
              n.payload_json AS payloadJson,
              n.lmd_amount AS lmdAmount,
              n.lmd_claim_expire_at AS lmdClaimExpireAt,
              n.expire_at AS expireAt,
              n.created_at AS notificationCreatedAt
            FROM `site_notification_user` nu
            INNER JOIN `site_notification` n ON n.id = nu.notification_id
            WHERE nu.user_id = #{userId}
              AND n.status = 'SENT'
              AND (n.expire_at IS NULL OR n.expire_at &gt; NOW())
              <if test="read != null">
                AND nu.`read` = #{read}
              </if>
            ORDER BY nu.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<UserInboxItem> selectInbox(
            @Param("userId") long userId,
            @Param("read") Integer read,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Data
    class UserInboxItem {
        private Long id;
        private Long notificationId;
        private Long userId;
        private Integer read;
        private LocalDateTime readAt;
        private Integer claimed;
        private LocalDateTime claimedAt;
        private LocalDateTime createdAt;
        private String type;
        private String title;
        private String content;
        private String level;
        private String linkUrl;
        private String payloadJson;
        private Long lmdAmount;
        private LocalDateTime lmdClaimExpireAt;
        private LocalDateTime expireAt;
        private LocalDateTime notificationCreatedAt;
    }
}
