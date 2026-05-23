package io.arknights.dateorfriends.modules.admin.notice.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NoticeReadMapper {

    @Insert("""
            INSERT IGNORE INTO `notice_read` (
              notice_id,
              user_id,
              read_at
            )
            VALUES (
              #{noticeId},
              #{userId},
              #{readAt}
            )
            """)
    int insertIgnore(@Param("noticeId") long noticeId, @Param("userId") long userId, @Param("readAt") LocalDateTime readAt);

    @Select("""
            <script>
            SELECT notice_id
            FROM `notice_read`
            WHERE user_id = #{userId}
              AND notice_id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    List<Long> selectReadNoticeIds(@Param("userId") long userId, @Param("ids") List<Long> ids);
}
