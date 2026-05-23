-- module: modules/admin/notice
-- description: 公告系统（发布/阅读/审计）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `notice`;
CREATE TABLE `notice` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',

  `title` VARCHAR(128) NOT NULL COMMENT '标题',
  `content` TEXT NOT NULL COMMENT '内容（支持富文本/markdown/纯文本；前端按原样展示）',
  `level` ENUM('NORMAL','IMPORTANT') NOT NULL DEFAULT 'NORMAL' COMMENT '等级：NORMAL 普通；IMPORTANT 重要（可触发强提醒）',
  `status` ENUM('DRAFT','PUBLISHED','OFFLINE') NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿；PUBLISHED 已发布；OFFLINE 已下线',
  `pinned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0 否；1 是',
  `publish_at` DATETIME NULL COMMENT '发布时间（PUBLISHED 时必有；NULL 表示未发布）',
  `expire_at` DATETIME NULL COMMENT '过期时间（NULL 表示不过期）',

  `created_by` BIGINT NOT NULL COMMENT '创建人ID（user.id）',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改人ID（user.id）',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0 否；1 是（软删除）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_notice_status` (`status`) COMMENT '按状态查询',
  KEY `idx_notice_level` (`level`) COMMENT '按等级查询',
  KEY `idx_notice_pinned` (`pinned`) COMMENT '按置顶查询',
  KEY `idx_notice_publish_at` (`publish_at`) COMMENT '按发布时间查询',
  KEY `idx_notice_expire_at` (`expire_at`) COMMENT '按过期时间查询',
  KEY `idx_notice_created_by` (`created_by`) COMMENT '按创建人查询',
  FULLTEXT KEY `ft_notice_keyword` (`title`, `content`) COMMENT '关键词全文索引',
  CONSTRAINT `fk_notice_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_notice_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告表';

DROP TABLE IF EXISTS `notice_read`;
CREATE TABLE `notice_read` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `notice_id` BIGINT NOT NULL COMMENT '公告ID（notice.id）',
  `user_id` BIGINT NOT NULL COMMENT '用户ID（user.id）',
  `read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_read_notice_user` (`notice_id`, `user_id`) COMMENT '用户对公告唯一已读',
  KEY `idx_notice_read_user_id` (`user_id`) COMMENT '按用户查询',
  KEY `idx_notice_read_read_at` (`read_at`) COMMENT '按阅读时间查询',
  CONSTRAINT `fk_notice_read_notice` FOREIGN KEY (`notice_id`) REFERENCES `notice` (`id`),
  CONSTRAINT `fk_notice_read_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告已读记录（只增不改）';

DROP TABLE IF EXISTS `notice_operation_log`;
CREATE TABLE `notice_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `notice_id` BIGINT NOT NULL COMMENT '公告ID（notice.id）',
  `actor_id` BIGINT NULL COMMENT '操作人ID（管理员 user.id；系统自动为 NULL）',
  `actor_role` VARCHAR(32) NULL COMMENT '操作人角色（ADMIN 等；系统自动为 NULL）',
  `action_type` ENUM('CREATE','UPDATE','PUBLISH','OFFLINE','DELETE') NOT NULL COMMENT '操作类型：CREATE/UPDATE/PUBLISH/OFFLINE/DELETE',
  `ip` VARCHAR(64) NULL COMMENT '客户端IP（基于请求头解析）',
  `detail` VARCHAR(512) NULL COMMENT '操作说明（简要描述）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_notice_op_notice_id` (`notice_id`) COMMENT '按公告查询',
  KEY `idx_notice_op_actor_id` (`actor_id`) COMMENT '按操作人查询',
  KEY `idx_notice_op_created_at` (`created_at`) COMMENT '按操作时间查询',
  CONSTRAINT `fk_notice_op_notice` FOREIGN KEY (`notice_id`) REFERENCES `notice` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告操作审计日志（只读追加）';

SET FOREIGN_KEY_CHECKS = 1;

