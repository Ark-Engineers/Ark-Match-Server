-- module: modules/user/online_room
-- description: 联机房间配置（房间元数据 + 白名单），用于后端重启后保持房间与在线状态

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

CREATE TABLE IF NOT EXISTS `online_room` (
  `room_id` VARCHAR(32) NOT NULL COMMENT '房间ID（唯一；URL/接口中使用）',
  `name` VARCHAR(32) NOT NULL COMMENT '房间名称（展示用）',
  `online` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '在线状态：0=离线；1=在线',
  `permission` VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '进入权限：PUBLIC/ADMIN_ONLY/PASSWORD/WHITELIST',
  `capacity` INT NOT NULL DEFAULT 0 COMMENT '人数上限：0=不限制',
  `password_hash` VARCHAR(64) NULL COMMENT '房间密码哈希（SHA-256 Hex；仅 permission=PASSWORD 时使用）',
  `creator_user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户ID（user.id）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已删除：0=未删除；1=已删除（逻辑删除标记）',
  PRIMARY KEY (`room_id`),
  KEY `idx_online_room_online` (`online`) COMMENT '按在线状态筛选',
  KEY `idx_online_room_permission` (`permission`) COMMENT '按权限筛选',
  KEY `idx_online_room_deleted` (`deleted`) COMMENT '按删除标记筛选',
  KEY `idx_online_room_updated_at` (`updated_at`) COMMENT '按更新时间排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联机房间配置表（持久化房间元数据）';

CREATE TABLE IF NOT EXISTS `online_room_whitelist` (
  `room_id` VARCHAR(32) NOT NULL COMMENT '房间ID（online_room.room_id）',
  `user_id` BIGINT NOT NULL COMMENT '白名单用户ID（user.id）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`room_id`, `user_id`),
  KEY `idx_online_room_whitelist_user_id` (`user_id`) COMMENT '按用户查询其可进入的房间',
  CONSTRAINT `fk_online_room_whitelist_room` FOREIGN KEY (`room_id`) REFERENCES `online_room` (`room_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_online_room_whitelist_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联机房间白名单（permission=WHITELIST 时生效）';

SET FOREIGN_KEY_CHECKS = 1;
