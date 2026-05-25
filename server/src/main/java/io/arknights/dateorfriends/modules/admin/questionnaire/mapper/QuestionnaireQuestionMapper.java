package io.arknights.dateorfriends.modules.admin.questionnaire.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuestionnaireQuestionMapper {

    @Delete("""
            DELETE FROM `questionnaire_question`
            WHERE questionnaire_id = #{questionnaireId}
            """)
    int deleteByQuestionnaireId(@Param("questionnaireId") long questionnaireId);

    @Insert("""
            <script>
            INSERT INTO `questionnaire_question` (
              questionnaire_id,
              seq,
              question_text,
              question_type,
              options_text,
              parent_seq,
              trigger_option,
              weight
            )
            VALUES
            <foreach collection="items" item="i" separator=",">
              (
                #{i.questionnaireId},
                #{i.seq},
                #{i.questionText},
                #{i.questionType},
                #{i.optionsText},
                #{i.parentSeq},
                #{i.triggerOption},
                #{i.weight}
              )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<QuestionnaireQuestionDO> items);

    @Select("""
            SELECT
              id,
              questionnaire_id AS questionnaireId,
              seq,
              question_text AS questionText,
              question_type AS questionType,
              options_text AS optionsText,
              parent_seq AS parentSeq,
              trigger_option AS triggerOption,
              weight,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `questionnaire_question`
            WHERE questionnaire_id = #{questionnaireId}
            ORDER BY parent_seq ASC, seq ASC, id ASC
            """)
    List<QuestionnaireQuestionDO> selectByQuestionnaireId(@Param("questionnaireId") long questionnaireId);
}
