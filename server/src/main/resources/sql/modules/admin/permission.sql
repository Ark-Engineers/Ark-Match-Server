-- module: modules/admin/permission
-- description: 超级管理员授权/撤销管理员操作日志表

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `admin_role_operation_log`;
CREATE TABLE `admin_role_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `actor_id` BIGINT NULL COMMENT '操作人ID（user.id；系统自动为 NULL）',
  `actor_role` VARCHAR(32) NOT NULL COMMENT '操作人角色（当前仅 SUPER_ADMIN）',
  `target_user_id` BIGINT NOT NULL COMMENT '被授权/撤销的目标用户ID（user.id）',
  `action_type` VARCHAR(32) NOT NULL COMMENT '操作类型：GRANT_ADMIN=授权管理员；REVOKE_ADMIN=撤销管理员',
  `from_role` VARCHAR(32) NOT NULL COMMENT '变更前角色（USER/ADMIN/SUPER_ADMIN）',
  `to_role` VARCHAR(32) NOT NULL COMMENT '变更后角色（USER/ADMIN/SUPER_ADMIN）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_role_op_target_user_id` (`target_user_id`) COMMENT '按目标用户查询',
  KEY `idx_admin_role_op_actor_id` (`actor_id`) COMMENT '按操作人查询',
  KEY `idx_admin_role_op_created_at` (`created_at`) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员角色变更操作日志（只读追加）';

SET FOREIGN_KEY_CHECKS = 1;
