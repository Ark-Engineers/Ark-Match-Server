package io.arknights.dateorfriends.modules.user.appeal.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface BanAppealMapper {

    @Insert("""
            INSERT INTO `ban_appeal` (
              account,
              user_id,
              contact,
              content,
              status,
              ip
            )
            VALUES (
              #{account},
              #{userId},
              #{contact},
              #{content},
              #{status},
              #{ip}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BanAppealDO appeal);
}

