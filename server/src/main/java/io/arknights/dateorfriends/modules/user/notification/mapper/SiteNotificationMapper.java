package io.arknights.dateorfriends.modules.user.notification.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

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
              #{status},
              #{expireAt},
              #{createdBy},
              #{createdAt},
              #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SiteNotificationDO notification);
}

