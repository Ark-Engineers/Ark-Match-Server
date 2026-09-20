-- module: modules/admin/spine
-- description: Spine 资源导入管理（元数据 + 文件清单）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `spine_asset_file`;
DROP TABLE IF EXISTS `spine_asset`;

CREATE TABLE `spine_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',

  `asset_key` VARCHAR(64) NOT NULL COMMENT '资源标识（由 atlas/skel 文件名推断；全局唯一）',
  `name` VARCHAR(128) NULL COMMENT '展示名称（可为空）',
  `type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1=人物，2=敌人，3=BOSS',

  `created_by` BIGINT NOT NULL COMMENT '创建人ID（管理员 user.id）',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改人ID（管理员 user.id）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spine_asset_key` (`asset_key`) COMMENT '资源标识唯一',
  KEY `idx_spine_asset_created_at` (`created_at`) COMMENT '按创建时间排序',
  KEY `idx_spine_asset_updated_at` (`updated_at`) COMMENT '按更新时间排序',
  KEY `idx_spine_asset_created_by` (`created_by`) COMMENT '按创建人筛选',
  CONSTRAINT `fk_spine_asset_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_spine_asset_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Spine 资源元数据表';

CREATE TABLE `spine_asset_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `asset_id` BIGINT NOT NULL COMMENT '资源ID（spine_asset.id）',

  `file_type` ENUM('ATLAS','SKEL','PNG','OTHER') NOT NULL COMMENT '文件类型：ATLAS/SKEL/PNG/OTHER',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名（上传时）',
  `stored_name` VARCHAR(255) NOT NULL COMMENT '存储文件名（落盘）',
  `relative_path` VARCHAR(512) NOT NULL COMMENT '相对路径（相对存储根目录，如 key/xxx.png）',
  `size_bytes` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `mime_type` VARCHAR(128) NULL COMMENT 'MIME类型',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  KEY `idx_spine_asset_file_asset_id` (`asset_id`) COMMENT '按资源查询文件清单',
  KEY `idx_spine_asset_file_type` (`file_type`) COMMENT '按文件类型筛选',
  UNIQUE KEY `uk_spine_asset_file_path` (`asset_id`, `relative_path`) COMMENT '同资源内相对路径唯一',
  CONSTRAINT `fk_spine_asset_file_asset` FOREIGN KEY (`asset_id`) REFERENCES `spine_asset` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Spine 资源文件清单';

SET FOREIGN_KEY_CHECKS = 1;
