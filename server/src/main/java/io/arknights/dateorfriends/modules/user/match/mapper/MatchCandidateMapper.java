package io.arknights.dateorfriends.modules.user.match.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatchCandidateMapper {

    @Select("""
            SELECT
              u.id AS userId,
              u.nickname,
              u.avatar_url AS avatarUrl,
              u.last_login_ip AS lastLoginIp,
              p.region_ip AS regionIp,
              p.birthday,
              p.birthday_visible AS birthdayVisible,
              p.tags_json AS tagsJson
            FROM `user` u
            LEFT JOIN `user_profile` p ON p.user_id = u.id
            WHERE u.deleted = 0
              AND u.status = 'NORMAL'
              AND u.id <> #{selfUserId}
            ORDER BY u.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<MatchCandidateDO> selectCandidates(
            @Param("selfUserId") long selfUserId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}

