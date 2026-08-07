package io.arknights.dateorfriends.modules.user.questionnaire.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserQuestionnaireAnswerDO {
    private Long id;
    private Long userId;
    private Long questionnaireId;
    private String status;
    private Integer activeFlag;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

