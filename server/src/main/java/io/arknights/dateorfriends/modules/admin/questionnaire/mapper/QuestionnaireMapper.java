package io.arknights.dateorfriends.modules.admin.questionnaire.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QuestionnaireMapper {

    @Insert("""
            INSERT INTO `questionnaire` (
              title,
              subtitle,
              status,
              created_by,
              updated_by,
              deleted
            )
            VALUES (
              #{title},
              #{subtitle},
              #{status},
              #{createdBy},
              #{updatedBy},
              #{deleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QuestionnaireDO questionnaire);

    @Select("""
            SELECT
              id,
              title,
              subtitle,
              status,
              created_by AS createdBy,
              updated_by AS updatedBy,
              deleted,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `questionnaire`
            WHERE id = #{id}
              AND deleted = 0
            LIMIT 1
            """)
    QuestionnaireDO selectById(@Param("id") long id);

    @Select("""
            SELECT
              id,
              title,
              subtitle,
              status,
              created_by AS createdBy,
              updated_by AS updatedBy,
              deleted,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `questionnaire`
            WHERE deleted = 0
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<QuestionnaireDO> selectList(@Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT COUNT(1)
            FROM `questionnaire`
            WHERE deleted = 0
            """)
    long countAll();

    @Update("""
            UPDATE `questionnaire`
            SET
              title = #{title},
              subtitle = #{subtitle},
              status = #{status},
              updated_by = #{updatedBy}
            WHERE id = #{id}
              AND deleted = 0
            """)
    int updateMeta(
            @Param("id") long id,
            @Param("title") String title,
            @Param("subtitle") String subtitle,
            @Param("status") String status,
            @Param("updatedBy") long updatedBy
    );

    @Update("""
            UPDATE `questionnaire`
            SET
              deleted = 1,
              updated_by = #{updatedBy}
            WHERE id = #{id}
              AND deleted = 0
            """)
    int softDelete(@Param("id") long id, @Param("updatedBy") long updatedBy);
}

