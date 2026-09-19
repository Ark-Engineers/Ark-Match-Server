package io.arknights.dateorfriends.modules.user.arknights.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ArknightsBindingMapper {

    @Select("""
            SELECT
              user_id AS userId,
              arknights_bound AS arknightsBound,
              arknights_is_minor AS arknightsIsMinor,
              arknights_hg_id AS arknightsHgId,
              arknights_uid AS arknightsUid,
              arknights_nickname AS arknightsNickname,
              arknights_channel_name AS arknightsChannelName,
              arknights_bound_at AS arknightsBoundAt
            FROM user_profile
            WHERE user_id = #{userId}
            LIMIT 1
            """)
    ArknightsBindingDO selectByUserId(@Param("userId") long userId);

    @Select("""
            SELECT
              user_id AS userId,
              arknights_bound AS arknightsBound,
              arknights_is_minor AS arknightsIsMinor,
              arknights_hg_id AS arknightsHgId,
              arknights_uid AS arknightsUid,
              arknights_nickname AS arknightsNickname,
              arknights_channel_name AS arknightsChannelName,
              arknights_bound_at AS arknightsBoundAt
            FROM user_profile
            WHERE arknights_uid = #{uid}
              AND arknights_bound = 1
            LIMIT 1
            """)
    ArknightsBindingDO selectBoundByUid(@Param("uid") String uid);

    @Insert("""
            INSERT INTO user_profile (
              user_id,
              arknights_bound,
              arknights_is_minor,
              arknights_hg_id,
              arknights_uid,
              arknights_nickname,
              arknights_channel_name,
              arknights_bound_at
            )
            VALUES (
              #{b.userId},
              #{b.arknightsBound},
              #{b.arknightsIsMinor},
              #{b.arknightsHgId},
              #{b.arknightsUid},
              #{b.arknightsNickname},
              #{b.arknightsChannelName},
              #{b.arknightsBoundAt}
            )
            """)
    int insert(@Param("b") ArknightsBindingDO binding);

    @Update("""
            UPDATE user_profile
            SET
              arknights_bound = #{b.arknightsBound},
              arknights_is_minor = #{b.arknightsIsMinor},
              arknights_hg_id = #{b.arknightsHgId},
              arknights_uid = #{b.arknightsUid},
              arknights_nickname = #{b.arknightsNickname},
              arknights_channel_name = #{b.arknightsChannelName},
              arknights_bound_at = #{b.arknightsBoundAt}
            WHERE user_id = #{b.userId}
            """)
    int update(@Param("b") ArknightsBindingDO binding);

    @Update("""
            UPDATE user_profile
            SET
              arknights_bound = 0,
              arknights_is_minor = NULL,
              arknights_hg_id = NULL,
              arknights_uid = NULL,
              arknights_nickname = NULL,
              arknights_channel_name = NULL,
              arknights_bound_at = NULL
            WHERE user_id = #{userId}
            """)
    int unbind(@Param("userId") long userId);
}
