-- module: modules/user/users
-- description: 用户/管理员账户与行为日志表（来自 users.sql 归档）

-- 用户数据库（MySQL 8.0）
-- 目的：提供一套最小可用的“用户/管理员”账户表结构
-- 说明：role 字段用于识别管理员与普通用户（USER / ADMIN）
-- 约定（后续所有表/字段都遵循）：
-- 1) 每张表、每个字段都必须写 COMMENT 备注（便于协作与维护）。
-- 2) 建议每张表都包含 created_at / updated_at（以及需要时的 deleted/deleted_at）。
 
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
 
-- 1) 创建数据库（如你已有数据库，可跳过本段）
CREATE DATABASE IF NOT EXISTS `ark_match`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
 
USE `ark_match`;
 
-- 2) 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
 
  -- 账号与权限
  `account` VARCHAR(64) NOT NULL COMMENT '登录账号（唯一；用于登录）',
  `email` VARCHAR(255) NOT NULL COMMENT '登录邮箱（唯一；建议用于找回密码/通知）',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希（BCrypt/Argon2 输出；禁止明文/可逆加密）',
  `role` ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER' COMMENT '角色：USER=普通用户；ADMIN=管理员（用于鉴权）',
 
  -- 展示信息
  `nickname` VARCHAR(64) NOT NULL COMMENT '昵称（展示用）',
  `avatar_url` VARCHAR(512) NULL COMMENT '头像URL（可为空；存对象存储/CDN 地址）',
 
  -- 状态与安全
  `status` ENUM('NORMAL','SUSPENDED','BANNED') NOT NULL DEFAULT 'NORMAL' COMMENT '账号状态：NORMAL=正常；SUSPENDED=暂停使用；BANNED=封禁',
  `email_verified_at` DATETIME NULL COMMENT '邮箱验证完成时间（未验证为 NULL）',
  `last_login_at` DATETIME NULL COMMENT '最近一次登录时间',
  `last_login_ip` VARCHAR(45) NULL COMMENT '最近一次登录IP（IPv4/IPv6；注意隐私与保留周期）',
  `login_fail_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数（用于防爆破/锁定策略）',
  `locked_until` DATETIME NULL COMMENT '锁定到期时间（到期前拒绝登录）',
 
  -- 审计与软删除
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（自动刷新）',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已删除：0=未删除；1=已删除（逻辑删除标记）',
  `deleted_at` DATETIME NULL COMMENT '软删除时间（NULL=未删除；非NULL=已注销/逻辑删除）',
 
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_email` (`email`) COMMENT '邮箱唯一索引（登录/找回密码）',
  UNIQUE KEY `uk_user_account` (`account`) COMMENT '账号唯一索引（登录）',
  KEY `idx_user_role` (`role`) COMMENT '角色索引（后台筛选/鉴权统计）',
  KEY `idx_user_status` (`status`) COMMENT '状态索引（封禁/暂停用户筛选）',
  KEY `idx_user_deleted` (`deleted`) COMMENT '删除标记索引（后台过滤/清理任务）',
  KEY `idx_user_deleted_at` (`deleted_at`) COMMENT '软删除索引（清理任务/过滤）',
  KEY `idx_user_last_login_at` (`last_login_at`) COMMENT '登录时间索引（活跃统计/后台筛选）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表（含管理员）';
 
 
-- 2.1) 行为日志表
-- 用途：记录用户关键行为（哪个用户、来自哪个 IP、调用了哪个接口）
DROP TABLE IF EXISTS `action_log`;
CREATE TABLE `action_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `user_id` BIGINT NOT NULL COMMENT '用户ID（关联 user.id）',
  `ip` VARCHAR(45) NOT NULL COMMENT '客户端IP（IPv4/IPv6）',
  `api` VARCHAR(128) NOT NULL COMMENT '使用的接口路径（如 /api/auth/login）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
 
  PRIMARY KEY (`id`),
  KEY `idx_action_log_user_id` (`user_id`) COMMENT '按用户查询行为记录',
  KEY `idx_action_log_created_at` (`created_at`) COMMENT '按时间范围查询',
  KEY `idx_action_log_ip` (`ip`) COMMENT '按IP查询（风控排查）',
  CONSTRAINT `fk_action_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行为日志';
 
-- 字段备注汇总（action_log）
-- id         : 主键，自增
-- user_id    : 用户ID（关联 user.id）
-- ip         : 客户端IP（IPv4/IPv6）
-- api        : 使用的接口路径（如 /api/auth/login）
-- created_at : 记录时间
 
-- 字段备注汇总（便于阅读，对应上面的 COMMENT）
-- id                : 主键，自增
-- account           : 登录账号（唯一；用于登录）
-- email             : 登录邮箱（唯一）
-- password_hash     : 密码哈希（BCrypt/Argon2）
-- role              : 角色（USER/ADMIN）——用于区分普通用户与管理员
-- nickname          : 昵称（展示用）
-- avatar_url        : 头像URL
-- status            : 账号状态（NORMAL/SUSPENDED/BANNED）
-- email_verified_at : 邮箱验证时间
-- last_login_at     : 最近登录时间
-- last_login_ip     : 最近登录IP
-- login_fail_count  : 连续失败次数
-- locked_until      : 锁定到期时间
-- created_at        : 创建时间
-- updated_at        : 更新时间
-- deleted           : 是否已删除（逻辑删除标记）
-- deleted_at        : 软删除时间
 
-- 3) 可选：管理员账号示例（请务必替换为你自己的哈希密码）
-- 生成 BCrypt 示例（仅说明，不在 SQL 中执行）：
--   Java/Spring Security: new BCryptPasswordEncoder().encode("你的强密码")
-- INSERT INTO `user`(account, email, password_hash, role, nickname, status)
-- VALUES ('admin', 'admin@example.com', '$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 'ADMIN', '管理员', 'NORMAL');
 
-- 3) 管理员硬性导入（MySQL）
-- 说明：
-- 1) 使用固定账号作为管理员账号标识（依赖 uk_user_account 唯一索引）。
-- 2) 若已存在同邮箱账号，则强制将其 role 置为 ADMIN，并恢复为可用状态。
-- 3) password_hash 必须是 BCrypt/Argon2 的哈希值，请务必替换下面的占位字符串。
INSERT INTO `user` (
  `account`,
  `email`,
  `password_hash`,
  `role`,
  `nickname`,
  `status`,
  `email_verified_at`,
  `login_fail_count`,
  `locked_until`,
  `deleted`,
  `deleted_at`
)
VALUES (
  'admin',
  'admin@example.com',
  '$2a$10$REPLACE_WITH_YOUR_BCRYPT_HASH................................',
  'ADMIN',
  '管理员',
  'NORMAL',
  NOW(),
  0,
  NULL,
  0,
  NULL
)
ON DUPLICATE KEY UPDATE
  `role` = 'ADMIN',
  `account` = VALUES(`account`),
  `email` = VALUES(`email`),
  `nickname` = VALUES(`nickname`),
  `status` = 'NORMAL',
  `email_verified_at` = COALESCE(`email_verified_at`, VALUES(`email_verified_at`)),
  `login_fail_count` = 0,
  `locked_until` = NULL,
  `deleted` = 0,
  `deleted_at` = NULL;
 
-- 4) 行动日志写入示例（由后端在关键接口中写入）
-- INSERT INTO `action_log`(`user_id`, `ip`, `api`)
-- VALUES (1, '127.0.0.1', '/api/auth/login');
 
SET FOREIGN_KEY_CHECKS = 1;
