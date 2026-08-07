package io.arknights.dateorfriends.modules.user.questionnaire.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserQuestionnaireAnswerItemMapper {

    @Insert("""
            <script>
            INSERT INTO `user_questionnaire_answer_item` (
              answer_id,
              question_seq,
              parent_seq,
              answer_text
            )
            VALUES
            <foreach collection="items" item="i" separator=",">
              (
                #{i.answerId},
                #{i.questionSeq},
                #{i.parentSeq},
                #{i.answerText}
              )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<UserQuestionnaireAnswerItemDO> items);

    @Select("""
            SELECT
              id,
              answer_id AS answerId,
              question_seq AS questionSeq,
              parent_seq AS parentSeq,
              answer_text AS answerText,
              created_at AS createdAt
            FROM `user_questionnaire_answer_item`
            WHERE answer_id = #{answerId}
            ORDER BY parent_seq ASC, question_seq ASC, id ASC
            """)
    List<UserQuestionnaireAnswerItemDO> selectByAnswerId(@Param("answerId") long answerId);

    @Select("""
            <script>
            SELECT
              id,
              answer_id AS answerId,
              question_seq AS questionSeq,
              parent_seq AS parentSeq,
              answer_text AS answerText,
              created_at AS createdAt
            FROM `user_questionnaire_answer_item`
            WHERE answer_id IN
              <foreach collection="answerIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY answer_id ASC, parent_seq ASC, question_seq ASC, id ASC
            </script>
            """)
    List<UserQuestionnaireAnswerItemDO> selectByAnswerIds(@Param("answerIds") List<Long> answerIds);
}

