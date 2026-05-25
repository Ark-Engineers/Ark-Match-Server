package io.arknights.dateorfriends.modules.admin.questionnaire.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionnaireQuestionDO {
    private Long id;
    private Long questionnaireId;
    private Integer seq;
    private String questionText;
    private String questionType;
    private String optionsText;
    private Integer parentSeq;
    private String triggerOption;
    private BigDecimal weight;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

