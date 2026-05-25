-- module: modules/admin/questionnaire
-- description: 问卷管理（导入导出/预览）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `questionnaire`;
CREATE TABLE `questionnaire` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',

  `title` VARCHAR(128) NOT NULL COMMENT '问卷标题',
  `subtitle` VARCHAR(255) NULL COMMENT '问卷副标题（可为空）',
  `status` ENUM('DRAFT','READY') NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿；READY 可预览',

  `created_by` BIGINT NOT NULL COMMENT '创建人ID（管理员 user.id）',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改人ID（管理员 user.id）',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0 否；1 是（软删除）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_questionnaire_status` (`status`) COMMENT '按状态查询',
  KEY `idx_questionnaire_deleted` (`deleted`) COMMENT '按删除标记查询',
  KEY `idx_questionnaire_created_at` (`created_at`) COMMENT '按创建时间查询',
  FULLTEXT KEY `ft_questionnaire_keyword` (`title`, `subtitle`) COMMENT '关键词全文索引',
  CONSTRAINT `fk_questionnaire_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_questionnaire_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问卷表';

DROP TABLE IF EXISTS `questionnaire_question`;
CREATE TABLE `questionnaire_question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `questionnaire_id` BIGINT NOT NULL COMMENT '问卷ID（questionnaire.id）',

  `seq` INT NOT NULL COMMENT '题目序号（只读、连续正整数；用于排序/关联）',
  `question_text` VARCHAR(255) NOT NULL COMMENT '问题（必填，>=2）',
  `question_type` VARCHAR(32) NOT NULL COMMENT '题型：单选/填空/判断/多选_X',
  `options_text` VARCHAR(1024) NULL COMMENT '选项答案：单选/多选用英文|分隔；填空/判断置空',

  `parent_seq` INT NOT NULL DEFAULT 0 COMMENT '主问题序号（子问题绑定；主问题为0）',
  `trigger_option` VARCHAR(255) NULL COMMENT '触发子问题选项（子问题使用；存父问题选项值）',

  `weight` DECIMAL(6,2) NOT NULL COMMENT '权重（最多2位小数；总和=100）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_questionnaire_parent_seq` (`questionnaire_id`, `parent_seq`, `seq`) COMMENT '同问卷内：主问题序号+子序号唯一（主问题 parent_seq=0）',
  KEY `idx_questionnaire_question_parent` (`questionnaire_id`, `parent_seq`, `seq`) COMMENT '按主问题序号查询子问题',
  CONSTRAINT `fk_questionnaire_question_questionnaire` FOREIGN KEY (`questionnaire_id`) REFERENCES `questionnaire` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问卷问题配置表';

SET FOREIGN_KEY_CHECKS = 1;
