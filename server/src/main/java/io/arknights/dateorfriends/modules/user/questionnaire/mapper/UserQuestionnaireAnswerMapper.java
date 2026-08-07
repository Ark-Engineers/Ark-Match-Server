package io.arknights.dateorfriends.modules.user.questionnaire.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserQuestionnaireAnswerMapper {

    @Select("""
            SELECT
              id,
              user_id AS userId,
              questionnaire_id AS questionnaireId,
              status,
              active_flag AS activeFlag,
              submitted_at AS submittedAt,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `user_questionnaire_answer`
            WHERE user_id = #{userId}
              AND active_flag = 1
            LIMIT 1
            """)
    UserQuestionnaireAnswerDO selectActiveByUserId(@Param("userId") long userId);

    @Select("""
            <script>
            SELECT
              id,
              user_id AS userId,
              questionnaire_id AS questionnaireId,
              status,
              active_flag AS activeFlag,
              submitted_at AS submittedAt,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `user_questionnaire_answer`
            WHERE questionnaire_id = #{questionnaireId}
              AND active_flag = 1
              AND user_id IN
              <foreach collection="userIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    List<UserQuestionnaireAnswerDO> selectActiveByUserIds(
            @Param("questionnaireId") long questionnaireId,
            @Param("userIds") List<Long> userIds
    );

    @Update("""
            UPDATE `user_questionnaire_answer`
            SET
              status = 'DISCARDED',
              active_flag = NULL
            WHERE user_id = #{userId}
              AND active_flag = 1
            """)
    int discardActiveByUserId(@Param("userId") long userId);

    @Insert("""
            INSERT INTO `user_questionnaire_answer` (
              user_id,
              questionnaire_id,
              status,
              active_flag,
              submitted_at
            )
            VALUES (
              #{userId},
              #{questionnaireId},
              #{status},
              #{activeFlag},
              #{submittedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserQuestionnaireAnswerDO answer);
}

