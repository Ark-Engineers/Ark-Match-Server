package io.arknights.dateorfriends.modules.admin.questionnaire.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionnaireDO {
    private Long id;
    private String title;
    private String subtitle;
    private String status;
    private Long createdBy;
    private Long updatedBy;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

