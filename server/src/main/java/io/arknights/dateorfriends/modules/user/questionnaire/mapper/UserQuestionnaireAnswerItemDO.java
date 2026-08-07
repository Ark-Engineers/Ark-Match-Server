package io.arknights.dateorfriends.modules.user.questionnaire.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserQuestionnaireAnswerItemDO {
    private Long id;
    private Long answerId;
    private Integer questionSeq;
    private Integer parentSeq;
    private String answerText;
    private LocalDateTime createdAt;
}

