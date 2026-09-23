package io.arknights.dateorfriends.modules.user.online.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReportMapper {

    @Insert("""
            INSERT INTO report (reporter_user_id, reported_user_id, report_type, content, room_id, chat_message_id, status, created_at)
            VALUES (#{reporterUserId}, #{reportedUserId}, #{reportType}, #{content}, #{roomId}, #{chatMessageId}, #{status}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReportDO report);

    @Select("""
            <script>
            SELECT COUNT(*) FROM report
            <where>
                <if test='status != null'>AND status = #{status}</if>
                <if test='reportType != null'>AND report_type = #{reportType}</if>
                <if test='reportedUserId != null'>AND reported_user_id = #{reportedUserId}</if>
                <if test='reporterUserId != null'>AND reporter_user_id = #{reporterUserId}</if>
                <if test='keyword != null and keyword != ""'>
                    AND content LIKE CONCAT('%', #{keyword}, '%')
                </if>
            </where>
            </script>
            """)
    long count(@Param("status") String status,
               @Param("reportType") String reportType,
               @Param("reportedUserId") Long reportedUserId,
               @Param("reporterUserId") Long reporterUserId,
               @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT * FROM report
            <where>
                <if test='status != null'>AND status = #{status}</if>
                <if test='reportType != null'>AND report_type = #{reportType}</if>
                <if test='reportedUserId != null'>AND reported_user_id = #{reportedUserId}</if>
                <if test='reporterUserId != null'>AND reporter_user_id = #{reporterUserId}</if>
                <if test='keyword != null and keyword != ""'>
                    AND content LIKE CONCAT('%', #{keyword}, '%')
                </if>
            </where>
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ReportDO> selectList(@Param("status") String status,
                              @Param("reportType") String reportType,
                              @Param("reportedUserId") Long reportedUserId,
                              @Param("reporterUserId") Long reporterUserId,
                              @Param("keyword") String keyword,
                              @Param("limit") int limit,
                              @Param("offset") int offset);

    @Select("SELECT * FROM report WHERE id = #{id}")
    ReportDO selectById(@Param("id") long id);

    @Update("""
            UPDATE report SET status = #{status}, handled_by = #{handledBy}, handled_at = #{handledAt},
            action_taken = #{actionTaken}, action_detail = #{actionDetail}
            WHERE id = #{id}
            """)
    int updateHandled(@Param("id") long id,
                      @Param("status") String status,
                      @Param("handledBy") Long handledBy,
                      @Param("handledAt") LocalDateTime handledAt,
                      @Param("actionTaken") String actionTaken,
                      @Param("actionDetail") String actionDetail);
}
