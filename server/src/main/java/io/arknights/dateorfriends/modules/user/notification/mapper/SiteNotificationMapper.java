package io.arknights.dateorfriends.modules.user.notification.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SiteNotificationMapper {

    @Insert("""
            INSERT INTO `site_notification` (
              type,
              title,
              content,
              level,
              link_url,
              payload_json,
              lmd_amount,
              lmd_claim_expire_at,
              status,
              expire_at,
              created_by,
              created_at,
              updated_at
            )
            VALUES (
              #{type},
              #{title},
              #{content},
              #{level},
              #{linkUrl},
              #{payloadJson},
              #{lmdAmount},
              #{lmdClaimExpireAt},
              #{status},
              #{expireAt},
              #{createdBy},
              #{createdAt},
              #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SiteNotificationDO notification);

    @Select("""
            SELECT
              id,
              type,
              title,
              content,
              level,
              link_url AS linkUrl,
              payload_json AS payloadJson,
              lmd_amount AS lmdAmount,
              lmd_claim_expire_at AS lmdClaimExpireAt,
              status,
              expire_at AS expireAt,
              created_by AS createdBy,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `site_notification`
            WHERE id = #{id}
            LIMIT 1
            """)
    SiteNotificationDO selectById(@Param("id") long id);
}

