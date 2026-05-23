-- module: modules/admin/user_manage
-- description: 用户管理（仅超级管理员）审计日志表

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `user_manage_operation_log`;
CREATE TABLE `user_manage_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',

  `actor_id` BIGINT NULL COMMENT '操作人ID（管理员 user.id；系统自动为 NULL）',
  `actor_role` VARCHAR(32) NULL COMMENT '操作人角色（ADMIN/SUPER_ADMIN 等；系统自动为 NULL）',
  `target_user_id` BIGINT NOT NULL COMMENT '被操作的目标用户ID（user.id）',

  `action_type` VARCHAR(64) NOT NULL COMMENT '操作类型（如 UPDATE_PROFILE/RESET_PASSWORD/DEACTIVATE/BAN/UNBAN/GRANT_ADMIN/REVOKE_ADMIN 等）',
  `ip` VARCHAR(64) NULL COMMENT '客户端IP（基于请求头解析）',
  `detail` VARCHAR(512) NULL COMMENT '简要说明（便于后台直接展示）',
  `diff_json` JSON NULL COMMENT '字段级变更详情（before/after）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

  PRIMARY KEY (`id`),
  KEY `idx_user_manage_op_target_user_id` (`target_user_id`) COMMENT '按目标用户查询',
  KEY `idx_user_manage_op_actor_id` (`actor_id`) COMMENT '按操作人查询',
  KEY `idx_user_manage_op_action_type` (`action_type`) COMMENT '按操作类型筛选',
  KEY `idx_user_manage_op_created_at` (`created_at`) COMMENT '按时间查询',
  CONSTRAINT `fk_user_manage_op_target_user` FOREIGN KEY (`target_user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_user_manage_op_actor` FOREIGN KEY (`actor_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户管理操作审计日志（只读追加）';

SET FOREIGN_KEY_CHECKS = 1;
