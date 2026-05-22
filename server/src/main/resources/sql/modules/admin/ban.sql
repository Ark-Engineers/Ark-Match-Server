-- module: modules/admin/ban
-- description: IP/邮箱封禁记录表

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `ban_record`;
CREATE TABLE `ban_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',

  `target_type` ENUM('IP','EMAIL','USER') NOT NULL COMMENT '封禁目标类型：IP/EMAIL/USER',
  `target_value` VARCHAR(255) NOT NULL COMMENT '封禁目标值（IP/邮箱/用户ID）',
  `banned_user_id` BIGINT NULL COMMENT '被封禁的用户ID（user.id；IP/EMAIL 可能为空）',
  `report_id` BIGINT NOT NULL COMMENT '关联举报单ID',

  `admin_id` BIGINT NOT NULL COMMENT '操作管理员ID（user.id）',
  `reason` VARCHAR(255) NULL COMMENT '封禁原因',

  `duration_seconds` BIGINT NULL COMMENT '封禁时长（秒；NULL 表示永久）',
  `effective_at` DATETIME NOT NULL COMMENT '封禁生效时间',
  `expires_at` DATETIME NULL COMMENT '封禁到期时间（NULL 表示永久）',

  `status` ENUM('ACTIVE','EXPIRED','REVOKED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 生效中；EXPIRED 到期自动解封；REVOKED 手动解封',
  `unbanned_at` DATETIME NULL COMMENT '解封时间（手动/自动）',
  `unbanned_by` BIGINT NULL COMMENT '手动解封管理员ID（自动解封为 NULL）',
  `unban_type` ENUM('AUTO','MANUAL') NULL COMMENT '解封类型：AUTO 自动；MANUAL 手动',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_ban_record_target` (`target_type`, `target_value`) COMMENT '按目标查询',
  KEY `idx_ban_record_banned_user_id` (`banned_user_id`) COMMENT '按被封用户查询',
  KEY `idx_ban_record_report_id` (`report_id`) COMMENT '按举报单查询',
  KEY `idx_ban_record_status` (`status`) COMMENT '按状态查询',
  KEY `idx_ban_record_effective_at` (`effective_at`) COMMENT '按生效时间查询',
  KEY `idx_ban_record_expires_at` (`expires_at`) COMMENT '按到期时间查询',
  KEY `idx_ban_record_admin_id` (`admin_id`) COMMENT '按管理员查询',
  FULLTEXT KEY `ft_ban_record_keyword` (`target_value`, `reason`) COMMENT '关键词全文索引',
  CONSTRAINT `fk_ban_record_admin` FOREIGN KEY (`admin_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='封禁记录表';

DROP TABLE IF EXISTS `ban_operation_log`;
CREATE TABLE `ban_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `record_id` BIGINT NOT NULL COMMENT '封禁记录ID（ban_record.id）',
  `actor_id` BIGINT NULL COMMENT '操作人ID（管理员 user.id；系统自动为 NULL）',
  `actor_role` VARCHAR(32) NULL COMMENT '操作人角色（ADMIN 等；系统自动为 NULL）',
  `action_type` ENUM('BAN','UNBAN_MANUAL','UNBAN_AUTO') NOT NULL COMMENT '操作类型：BAN 封禁；UNBAN_MANUAL 手动解封；UNBAN_AUTO 到期自动解封',
  `from_status` ENUM('ACTIVE','EXPIRED','REVOKED') NULL COMMENT '变更前状态（BAN 时为 NULL）',
  `to_status` ENUM('ACTIVE','EXPIRED','REVOKED') NOT NULL COMMENT '变更后状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_ban_op_record_id` (`record_id`) COMMENT '按封禁记录查询',
  KEY `idx_ban_op_actor_id` (`actor_id`) COMMENT '按操作人查询',
  KEY `idx_ban_op_created_at` (`created_at`) COMMENT '按操作时间查询',
  CONSTRAINT `fk_ban_op_record` FOREIGN KEY (`record_id`) REFERENCES `ban_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='封禁操作日志（只读追加）';

SET FOREIGN_KEY_CHECKS = 1;
