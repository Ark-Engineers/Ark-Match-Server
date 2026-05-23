-- module: modules/user/notification
-- description: 站内通知（收件箱）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `site_notification`;
CREATE TABLE `site_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',

  `type` VARCHAR(64) NOT NULL COMMENT '通知类型（如 SYSTEM/SECURITY/ACCOUNT 等）',
  `title` VARCHAR(128) NOT NULL COMMENT '标题',
  `content` TEXT NOT NULL COMMENT '内容（按原样展示）',
  `level` ENUM('NORMAL','IMPORTANT') NOT NULL DEFAULT 'NORMAL' COMMENT '等级：NORMAL 普通；IMPORTANT 重要',
  `link_url` VARCHAR(512) NULL COMMENT '跳转链接（站内路由/外链）',
  `payload_json` JSON NULL COMMENT '结构化扩展数据（前端可按 type 解析）',

  `status` ENUM('SENT','OFFLINE') NOT NULL DEFAULT 'SENT' COMMENT '状态：SENT 已发送；OFFLINE 下线/撤回',
  `expire_at` DATETIME NULL COMMENT '过期时间（NULL 表示不过期）',

  `created_by` BIGINT NULL COMMENT '创建人ID（管理员 user.id；系统创建为 NULL）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_site_notif_type` (`type`) COMMENT '按类型查询',
  KEY `idx_site_notif_level` (`level`) COMMENT '按等级查询',
  KEY `idx_site_notif_status` (`status`) COMMENT '按状态查询',
  KEY `idx_site_notif_expire_at` (`expire_at`) COMMENT '按过期时间查询',
  KEY `idx_site_notif_created_by` (`created_by`) COMMENT '按创建人查询',
  FULLTEXT KEY `ft_site_notif_keyword` (`title`, `content`) COMMENT '关键词全文索引',
  CONSTRAINT `fk_site_notif_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知主体';

DROP TABLE IF EXISTS `site_notification_user`;
CREATE TABLE `site_notification_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `notification_id` BIGINT NOT NULL COMMENT '通知ID（site_notification.id）',
  `user_id` BIGINT NOT NULL COMMENT '接收用户ID（user.id）',

  `read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读；1 已读',
  `read_at` DATETIME NULL COMMENT '阅读时间（未读为 NULL）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_notif_user_notif_user` (`notification_id`, `user_id`) COMMENT '同一用户同一通知唯一投递',
  KEY `idx_site_notif_user_user_id` (`user_id`) COMMENT '按用户查询收件箱',
  KEY `idx_site_notif_user_read` (`read`) COMMENT '按已读状态筛选',
  KEY `idx_site_notif_user_created_at` (`created_at`) COMMENT '按投递时间排序/翻页',
  CONSTRAINT `fk_site_notif_user_notif` FOREIGN KEY (`notification_id`) REFERENCES `site_notification` (`id`),
  CONSTRAINT `fk_site_notif_user_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知投递与已读状态（收件箱）';

SET FOREIGN_KEY_CHECKS = 1;
