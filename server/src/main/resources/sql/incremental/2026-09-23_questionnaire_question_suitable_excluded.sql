-- questionnaire_question: 新增合适状态/排除状态字段
ALTER TABLE `questionnaire_question`
  ADD COLUMN `is_suitable` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '合适状态：0 否；1 是' AFTER `weight`,
  ADD COLUMN `is_excluded` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '排除状态：0 否；1 是' AFTER `is_suitable`;
