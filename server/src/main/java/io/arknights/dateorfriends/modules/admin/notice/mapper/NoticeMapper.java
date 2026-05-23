package io.arknights.dateorfriends.modules.admin.notice.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NoticeMapper {

    @Insert("""
            INSERT INTO `notice` (
              title,
              content,
              level,
              status,
              pinned,
              publish_at,
              expire_at,
              created_by,
              updated_by,
              deleted
            )
            VALUES (
              #{title},
              #{content},
              #{level},
              #{status},
              #{pinned},
              #{publishAt},
              #{expireAt},
              #{createdBy},
              #{updatedBy},
              #{deleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NoticeDO notice);

    @Update("""
            UPDATE `notice`
            SET
              title = #{title},
              content = #{content},
              level = #{level},
              pinned = #{pinned},
              expire_at = #{expireAt},
              updated_by = #{updatedBy}
            WHERE id = #{id}
              AND deleted = 0
            """)
    int updateContent(
            @Param("id") long id,
            @Param("title") String title,
            @Param("content") String content,
            @Param("level") String level,
            @Param("pinned") int pinned,
            @Param("expireAt") LocalDateTime expireAt,
            @Param("updatedBy") long updatedBy
    );

    @Update("""
            UPDATE `notice`
            SET
              status = #{status},
              publish_at = #{publishAt},
              updated_by = #{updatedBy}
            WHERE id = #{id}
              AND deleted = 0
            """)
    int updateStatus(
            @Param("id") long id,
            @Param("status") String status,
            @Param("publishAt") LocalDateTime publishAt,
            @Param("updatedBy") long updatedBy
    );

    @Update("""
            UPDATE `notice`
            SET
              pinned = #{pinned},
              updated_by = #{updatedBy}
            WHERE id = #{id}
              AND deleted = 0
            """)
    int updatePinned(@Param("id") long id, @Param("pinned") int pinned, @Param("updatedBy") long updatedBy);

    @Update("""
            UPDATE `notice`
            SET
              deleted = 1,
              status = 'OFFLINE',
              updated_by = #{updatedBy}
            WHERE id = #{id}
              AND deleted = 0
            """)
    int softDelete(@Param("id") long id, @Param("updatedBy") long updatedBy);

    @Select("""
            SELECT
              id,
              title,
              content,
              level,
              status,
              pinned,
              publish_at AS publishAt,
              expire_at AS expireAt,
              created_by AS createdBy,
              updated_by AS updatedBy,
              deleted,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `notice`
            WHERE id = #{id}
              AND deleted = 0
            """)
    NoticeDO selectById(@Param("id") long id);

    @Select("""
            <script>
            SELECT
              COUNT(1)
            FROM `notice`
            WHERE deleted = 0
              <if test="keyword != null and keyword != ''">
                AND MATCH(title, content) AGAINST(#{keyword} IN BOOLEAN MODE)
              </if>
              <if test="status != null and status != ''">
                AND status = #{status}
              </if>
              <if test="level != null and level != ''">
                AND level = #{level}
              </if>
              <if test="pinned != null">
                AND pinned = #{pinned}
              </if>
              <if test="publishFrom != null">
                AND publish_at &gt;= #{publishFrom}
              </if>
              <if test="publishTo != null">
                AND publish_at &lt;= #{publishTo}
              </if>
            </script>
            """)
    long countAdminList(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("level") String level,
            @Param("pinned") Integer pinned,
            @Param("publishFrom") LocalDateTime publishFrom,
            @Param("publishTo") LocalDateTime publishTo
    );

    @Select("""
            <script>
            SELECT
              id,
              title,
              content,
              level,
              status,
              pinned,
              publish_at AS publishAt,
              expire_at AS expireAt,
              created_by AS createdBy,
              updated_by AS updatedBy,
              deleted,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `notice`
            WHERE deleted = 0
              <if test="keyword != null and keyword != ''">
                AND MATCH(title, content) AGAINST(#{keyword} IN BOOLEAN MODE)
              </if>
              <if test="status != null and status != ''">
                AND status = #{status}
              </if>
              <if test="level != null and level != ''">
                AND level = #{level}
              </if>
              <if test="pinned != null">
                AND pinned = #{pinned}
              </if>
              <if test="publishFrom != null">
                AND publish_at &gt;= #{publishFrom}
              </if>
              <if test="publishTo != null">
                AND publish_at &lt;= #{publishTo}
              </if>
            ORDER BY pinned DESC, publish_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<NoticeDO> selectAdminList(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("level") String level,
            @Param("pinned") Integer pinned,
            @Param("publishFrom") LocalDateTime publishFrom,
            @Param("publishTo") LocalDateTime publishTo,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT
              n.id,
              n.title,
              n.content,
              n.level,
              n.status,
              n.pinned,
              n.publish_at AS publishAt,
              n.expire_at AS expireAt,
              n.created_by AS createdBy,
              n.updated_by AS updatedBy,
              n.deleted,
              n.created_at AS createdAt,
              n.updated_at AS updatedAt
            FROM `notice` n
            WHERE n.deleted = 0
              AND n.status = 'PUBLISHED'
              AND n.publish_at IS NOT NULL
              AND n.publish_at &lt;= #{now}
              AND (n.expire_at IS NULL OR n.expire_at &gt; #{now})
              <if test="keyword != null and keyword != ''">
                AND MATCH(n.title, n.content) AGAINST(#{keyword} IN BOOLEAN MODE)
              </if>
            ORDER BY n.pinned DESC, n.publish_at DESC, n.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<NoticeDO> selectUserList(@Param("keyword") String keyword, @Param("now") LocalDateTime now, @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT
              COUNT(1)
            FROM `notice` n
            WHERE n.deleted = 0
              AND n.status = 'PUBLISHED'
              AND n.publish_at IS NOT NULL
              AND n.publish_at &lt;= #{now}
              AND (n.expire_at IS NULL OR n.expire_at &gt; #{now})
              <if test="keyword != null and keyword != ''">
                AND MATCH(n.title, n.content) AGAINST(#{keyword} IN BOOLEAN MODE)
              </if>
            </script>
            """)
    long countUserList(@Param("keyword") String keyword, @Param("now") LocalDateTime now);

    @Select("""
            SELECT
              n.id,
              n.title,
              n.content,
              n.level,
              n.status,
              n.pinned,
              n.publish_at AS publishAt,
              n.expire_at AS expireAt,
              n.created_by AS createdBy,
              n.updated_by AS updatedBy,
              n.deleted,
              n.created_at AS createdAt,
              n.updated_at AS updatedAt
            FROM `notice` n
            WHERE n.deleted = 0
              AND n.id = #{id}
              AND n.status = 'PUBLISHED'
              AND n.publish_at IS NOT NULL
              AND n.publish_at <= #{now}
              AND (n.expire_at IS NULL OR n.expire_at > #{now})
            """)
    NoticeDO selectUserDetail(@Param("id") long id, @Param("now") LocalDateTime now);

    @Select("""
            SELECT
              n.id,
              n.title,
              n.content,
              n.level,
              n.status,
              n.pinned,
              n.publish_at AS publishAt,
              n.expire_at AS expireAt,
              n.created_by AS createdBy,
              n.updated_by AS updatedBy,
              n.deleted,
              n.created_at AS createdAt,
              n.updated_at AS updatedAt
            FROM `notice` n
            LEFT JOIN `notice_read` r
              ON r.notice_id = n.id
             AND r.user_id = #{userId}
            WHERE n.deleted = 0
              AND n.status = 'PUBLISHED'
              AND n.level = 'IMPORTANT'
              AND n.publish_at IS NOT NULL
              AND n.publish_at <= #{now}
              AND (n.expire_at IS NULL OR n.expire_at > #{now})
              AND r.id IS NULL
            ORDER BY n.pinned DESC, n.publish_at DESC, n.id DESC
            LIMIT 1
            """)
    NoticeDO selectPopupImportantNotRead(@Param("userId") long userId, @Param("now") LocalDateTime now);
}

