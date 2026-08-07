-- module: modules/user/questionnaire_answer
-- description: 用户答卷表（仅保留1份有效答卷；历史答卷作废保留）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `user_questionnaire_answer_item`;
DROP TABLE IF EXISTS `user_questionnaire_answer`;

CREATE TABLE `user_questionnaire_answer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',

  `user_id` BIGINT NOT NULL COMMENT '用户ID（user.id）',
  `questionnaire_id` BIGINT NOT NULL COMMENT '问卷ID（questionnaire.id）',

  `status` ENUM('ACTIVE','DISCARDED') NOT NULL COMMENT '状态：ACTIVE=当前有效；DISCARDED=已作废（历史保留）',
  `active_flag` TINYINT NULL COMMENT '仅 ACTIVE 置 1；DISCARDED 置 NULL（用于唯一约束）',

  `submitted_at` DATETIME NOT NULL COMMENT '提交时间',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_active` (`user_id`, `active_flag`) COMMENT '每个用户只能存在1条 active_flag=1 的有效答卷',
  KEY `idx_uqa_user_questionnaire` (`user_id`, `questionnaire_id`) COMMENT '按用户+问卷查询',
  KEY `idx_uqa_questionnaire_status` (`questionnaire_id`, `status`) COMMENT '按问卷+状态查询',
  KEY `idx_uqa_submitted_at` (`submitted_at`) COMMENT '按提交时间排序/查询',
  CONSTRAINT `fk_uqa_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_uqa_questionnaire` FOREIGN KEY (`questionnaire_id`) REFERENCES `questionnaire` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户问卷答卷主表';

CREATE TABLE `user_questionnaire_answer_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',

  `answer_id` BIGINT NOT NULL COMMENT '答卷ID（user_questionnaire_answer.id）',
  `question_seq` INT NOT NULL COMMENT '题目序号（questionnaire_question.seq）',
  `parent_seq` INT NOT NULL DEFAULT 0 COMMENT '主问题序号（questionnaire_question.parent_seq；主问题=0）',
  `answer_text` VARCHAR(1024) NOT NULL COMMENT '答案：单选/判断=选项值或true/false；多选=英文|分隔；填空=文本',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uqai_answer_question` (`answer_id`, `parent_seq`, `question_seq`) COMMENT '同一答卷内同一题唯一',
  KEY `idx_uqai_answer_id` (`answer_id`) COMMENT '按答卷查询',
  CONSTRAINT `fk_uqai_answer` FOREIGN KEY (`answer_id`) REFERENCES `user_questionnaire_answer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户问卷答卷明细表';

SET FOREIGN_KEY_CHECKS = 1;

