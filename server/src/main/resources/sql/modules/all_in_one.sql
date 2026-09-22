-- module: modules/user/users
-- description: 用户/管理员账户与行为日志表（来自 users.sql 归档）

-- 用户数据库（MySQL 8.0）
-- 目的：提供一套最小可用的“用户/管理员”账户表结构
-- 说明：role 字段用于识别超级管理员/管理员与普通用户（USER / ADMIN / SUPER_ADMIN）
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
  `role` ENUM('USER','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'USER' COMMENT '角色：USER=普通用户；ADMIN=管理员；SUPER_ADMIN=超级管理员（用于鉴权与授权）',
 
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

USE `ark_match`;

ALTER TABLE `user`
  ADD COLUMN `avatar_char_id` VARCHAR(64) NULL AFTER `avatar_url`,
  ADD COLUMN `avatar_char_name` VARCHAR(64) NULL AFTER `avatar_char_id`;

-- module: modules/user/user_profile
-- description: 用户个人信息扩展表（个人主页/资料卡）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `user_profile`;
CREATE TABLE `user_profile` (
  `user_id` BIGINT NOT NULL COMMENT '用户ID（user.id）',

  `featured_role` VARCHAR(32) NULL COMMENT '主推角色（仅允许1个）',
  `signature` VARCHAR(255) NULL COMMENT '个性签名',

  `region_ip` VARCHAR(45) NULL COMMENT '地区来源IP（仅存IP；展示需解析到省市）',

  `birthday` DATE NULL COMMENT '生日（年月日）',
  `birthday_visible` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '生日是否对外公开：0=否；1=是',

  `tags_json` VARCHAR(256) NULL COMMENT '自定义标签JSON数组（最多3个）',

  `contact_pubkey_spki` TEXT NULL COMMENT '联系方式加密公钥（SPKI Base64）',
  `contact_privkey_pkcs8_enc` TEXT NULL COMMENT '联系方式解密私钥（PKCS8 Base64，经服务端主密钥加密存储）',
  `qq_enc` TEXT NULL COMMENT 'QQ密文（Base64）',
  `wechat_enc` TEXT NULL COMMENT '微信密文（Base64）',
  `email_enc` TEXT NULL COMMENT '邮箱密文（Base64）',

  `arknights_bound` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已绑定明日方舟：0=未绑定；1=已绑定',
  `arknights_is_minor` TINYINT(1) NULL COMMENT '鹰角原始未成年状态：0=成年；1=未成年；NULL=未知',
  `arknights_hg_id` VARCHAR(64) NULL COMMENT '森空岛ID（仅绑定用户本人可见）',
  `arknights_uid` VARCHAR(64) NULL COMMENT '明日方舟游戏UID（字符串，避免精度丢失）',
  `arknights_nickname` VARCHAR(128) NULL COMMENT '明日方舟游戏昵称（含编号后缀）',
  `arknights_channel_name` VARCHAR(64) NULL COMMENT '明日方舟区服名称',
  `arknights_bound_at` DATETIME NULL COMMENT '明日方舟最近一次绑定成功时间',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_profile_arknights_uid` (`arknights_uid`),
  KEY `idx_user_profile_arknights_bound` (`arknights_bound`),
  CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户个人信息扩展表';

SET FOREIGN_KEY_CHECKS = 1;

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

-- module: modules/admin/ping
-- description: Ping 示例表（演示建表/字段备注/逻辑删除）

USE `ark_match`;

CREATE TABLE IF NOT EXISTS `admin_ping_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message` VARCHAR(255) NOT NULL COMMENT '消息内容',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除(0未删除,1已删除)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端Ping日志表';

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

-- module: modules/user/questionnaire_answer
-- description: 用户答卷表（仅保留1份有效答卷；历史答卷作废保留）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `user_questionnaire_answer_item`;
DROP TABLE IF EXISTS `user_questionnaire_answer`;

CREATE TABLE `user_questionnaire_answer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',

  `user_id` BIGINT NOT NULL COMMENT '用户ID（user.id）',
  `questionnaire_id` BIGINT NOT NULL COMMENT '问卷ID（questionnaire.id）',

  `status` ENUM('ACTIVE','DISCARDED') NOT NULL COMMENT '状态：ACTIVE=当前有效；DISCARDED=已作废（历史保留）',
  `active_flag` TINYINT NULL COMMENT '仅 ACTIVE 置 1；DISCARDED 置 NULL（用于唯一约束）',

  `submitted_at` DATETIME NOT NULL COMMENT '提交时间',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_active` (`user_id`, `active_flag`) COMMENT '每个用户只能存在1条 active_flag=1 的有效答卷',
  KEY `idx_uqa_user_questionnaire` (`user_id`, `questionnaire_id`) COMMENT '按用户+问卷查询',
  KEY `idx_uqa_questionnaire_status` (`questionnaire_id`, `status`) COMMENT '按问卷+状态查询',
  KEY `idx_uqa_submitted_at` (`submitted_at`) COMMENT '按提交时间排序/查询',
  CONSTRAINT `fk_uqa_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_uqa_questionnaire` FOREIGN KEY (`questionnaire_id`) REFERENCES `questionnaire` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户问卷答卷主表';

CREATE TABLE `user_questionnaire_answer_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',

  `answer_id` BIGINT NOT NULL COMMENT '答卷ID（user_questionnaire_answer.id）',
  `question_seq` INT NOT NULL COMMENT '题目序号（questionnaire_question.seq）',
  `parent_seq` INT NOT NULL DEFAULT 0 COMMENT '主问题序号（questionnaire_question.parent_seq；主问题=0）',
  `answer_text` VARCHAR(1024) NOT NULL COMMENT '答案：单选/判断=选项值或true/false；多选=英文|分隔；填空=文本',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uqai_answer_question` (`answer_id`, `parent_seq`, `question_seq`) COMMENT '同一答卷内同一题唯一',
  KEY `idx_uqai_answer_id` (`answer_id`) COMMENT '按答卷查询',
  CONSTRAINT `fk_uqai_answer` FOREIGN KEY (`answer_id`) REFERENCES `user_questionnaire_answer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户问卷答卷明细表';

SET FOREIGN_KEY_CHECKS = 1;

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
  `lmd_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '龙门币奖励额度（0=无奖励）',
  `lmd_claim_expire_at` DATETIME NULL COMMENT '龙门币领取截止时间（NULL=永久有效）',

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
  `claimed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已领取龙门币：0 未领取；1 已领取',
  `claimed_at` DATETIME NULL COMMENT '龙门币领取时间（未领取为 NULL）',

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
  `report_id` BIGINT NULL COMMENT '关联举报单ID（预留；可为空）',

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

USE `ark_match`;

CREATE TABLE IF NOT EXISTS `ban_appeal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account` VARCHAR(64) NOT NULL COMMENT '申诉账号（用户登录账号）',
  `user_id` BIGINT NULL COMMENT '用户ID（若能匹配到）',
  `contact` VARCHAR(128) NULL COMMENT '联系方式（邮箱/手机号/微信/QQ 等）',
  `content` VARCHAR(2000) NOT NULL COMMENT '申诉内容',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING/RESOLVED/REJECTED',
  `handled_by` BIGINT NULL COMMENT '处理人ID（管理员）',
  `handled_at` DATETIME NULL COMMENT '处理时间',
  `handle_note` VARCHAR(2000) NULL COMMENT '处理备注',
  `ip` VARCHAR(64) NULL COMMENT '提交IP',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ban_appeal_account` (`account`),
  KEY `idx_ban_appeal_user_id` (`user_id`),
  KEY `idx_ban_appeal_status` (`status`),
  KEY `idx_ban_appeal_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='封禁申诉表（用户提交）';

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

-- module: modules/user/lmd_wallet
-- description: 龙门币账户、流水与邮件领取记录（类虚拟货币系统，余额=流水之和强一致）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `user_wallet`;
CREATE TABLE `user_wallet` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `user_id` BIGINT NOT NULL COMMENT '用户ID（user.id）',
  `balance` BIGINT NOT NULL DEFAULT 0 COMMENT '龙门币余额（非负；账面校验要求等于该用户流水之和）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（首次入账时建行）',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_wallet_user_id` (`user_id`) COMMENT '一个用户一个账户',
  CONSTRAINT `fk_user_wallet_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='龙门币账户';

DROP TABLE IF EXISTS `lmd_transaction`;
CREATE TABLE `lmd_transaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `user_id` BIGINT NOT NULL COMMENT '用户ID（user.id）',
  `amount` BIGINT NOT NULL COMMENT '变动金额（正数=入账；负数=出账；不得为0）',
  `balance_after` BIGINT NOT NULL COMMENT '本笔变动后的账户余额（账面校验用）',
  `type` VARCHAR(32) NOT NULL COMMENT '流水类型：MAIL_CLAIM 邮件领取；ADMIN_ADJUST 管理员调整',
  `ref_type` VARCHAR(32) NULL COMMENT '关联对象类型（如 NOTIFICATION；管理员调整为 NULL）',
  `ref_id` BIGINT NULL COMMENT '关联对象ID（如通知ID）',
  `description` VARCHAR(255) NULL COMMENT '备注说明（如管理员调整原因）',
  `trace_id` VARCHAR(64) NULL COMMENT '请求溯源ID（HTTP 响应头 X-Trace-Id，便于对账排查）',
  `request_ip` VARCHAR(45) NULL COMMENT '操作来源IP（IPv4/IPv6）',
  `created_by` BIGINT NULL COMMENT '操作人ID（user.id；管理员操作为管理员ID）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流水时间',
  PRIMARY KEY (`id`),
  KEY `idx_lmd_tx_user_id` (`user_id`) COMMENT '按用户查询流水',
  KEY `idx_lmd_tx_user_created` (`user_id`, `created_at`) COMMENT '按用户+时间翻页',
  KEY `idx_lmd_tx_ref` (`ref_type`, `ref_id`) COMMENT '按关联对象溯源',
  KEY `idx_lmd_tx_type` (`type`) COMMENT '按类型统计',
  CONSTRAINT `fk_lmd_tx_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='龙门币流水';

DROP TABLE IF EXISTS `lmd_mail_claim`;
CREATE TABLE `lmd_mail_claim` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `notification_id` BIGINT NOT NULL COMMENT '通知ID（site_notification.id）',
  `user_id` BIGINT NOT NULL COMMENT '领取用户ID（user.id）',
  `amount` BIGINT NOT NULL COMMENT '领取到的龙门币数量',
  `trace_id` VARCHAR(64) NULL COMMENT '请求溯源ID',
  `request_ip` VARCHAR(45) NULL COMMENT '领取来源IP',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lmd_mail_claim_notif_user` (`notification_id`, `user_id`) COMMENT '单用户单邮件仅可领取一次',
  KEY `idx_lmd_mail_claim_user_id` (`user_id`) COMMENT '按用户查询领取记录',
  KEY `idx_lmd_mail_claim_notif` (`notification_id`) COMMENT '按邮件查询领取名单',
  CONSTRAINT `fk_lmd_mail_claim_notif` FOREIGN KEY (`notification_id`) REFERENCES `site_notification` (`id`),
  CONSTRAINT `fk_lmd_mail_claim_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='龙门币邮件领取记录';

SET FOREIGN_KEY_CHECKS = 1;
-- module: modules/user/horse_race
-- description: 联机房间赛马竞猜模式（模式实例、轮次、下注、结算台账、控制审计；参赛对象直接引用 spine_asset）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `horse_race_control_log`;
DROP TABLE IF EXISTS `horse_race_settlement`;
DROP TABLE IF EXISTS `horse_race_bet`;
DROP TABLE IF EXISTS `horse_race_round`;
DROP TABLE IF EXISTS `horse_race`;

-- 赛马模式实例：单个联机房间内仅允许一个 ACTIVE 实例
CREATE TABLE `horse_race` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `room_id` VARCHAR(32) NOT NULL COMMENT '联机房间ID（online_room.room_id；服务层校验存在性）',
  `name` VARCHAR(64) NULL COMMENT '模式名称（展示用，可为空）',

  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '模式状态：ACTIVE=进行中；CLOSED=已结束/已关闭',
  `session_type` TINYINT NOT NULL DEFAULT 1 COMMENT '场次类型：1=一次性；2=限定场次数量；3=无限循环',
  `total_rounds` INT NOT NULL DEFAULT 1 COMMENT '总场次数（session_type=1 恒为1；=2 为配置值；=3 恒为0表示不限）',
  `participant_mode` TINYINT NOT NULL DEFAULT 1 COMMENT '参赛对象生成规则：1=手动选择；2=随机生成（均取自 spine_asset type 2/3）',
  `bet_duration_seconds` INT NOT NULL DEFAULT 120 COMMENT '单轮竞猜周期（秒；>=1且<=86400，服务层校验）',
  `pre_race_duration_seconds` INT NOT NULL DEFAULT 30 COMMENT '单轮预备时长（秒；>=0且<=86400，服务层校验）',
  `race_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '单轮比赛时长（秒；>=1且<=86400，服务层校验）',
  `podium_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '单轮领奖台时长（秒；>=1且<=86400，服务层校验）',

  `created_by` BIGINT NOT NULL COMMENT '创建人ID（管理员 user.id）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_horse_race_room` (`room_id`, `status`) COMMENT '按房间+状态查当前实例',
  KEY `idx_horse_race_status` (`status`) COMMENT '调度器按状态扫描',
  CONSTRAINT `fk_horse_race_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛马模式实例表（每房间一个进行中实例）';

-- 轮次：每轮 = 竞猜 + 预备 + 比赛 + 领奖台；四阶段时长在创建轮次时从模式配置保存为快照
-- 参赛名单不建表：每轮开赛时从 spine_asset（type 2/3）抓取 5 个 id 回写 racer_ids（逗号分隔，按道次排序；手动选择或随机生成），展示/下注/结算按 id 回查 spine_asset
CREATE TABLE `horse_race_round` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `race_id` BIGINT NOT NULL COMMENT '模式ID（horse_race.id）',
  `round_no` INT NOT NULL COMMENT '场次序号（从1递增）',
  `bet_duration_seconds` INT NOT NULL DEFAULT 120 COMMENT '本轮竞猜时长快照（秒；>=1且<=86400，服务层校验）',
  `pre_race_duration_seconds` INT NOT NULL DEFAULT 30 COMMENT '本轮预备时长快照（秒；>=0且<=86400，服务层校验）',
  `race_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '本轮比赛时长快照（秒；>=1且<=86400，服务层校验）',
  `podium_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '本轮领奖台时长快照（秒；>=1且<=86400，服务层校验）',
  `racer_ids` VARCHAR(255) NULL COMMENT '本场参赛 spine_asset.id（逗号分隔，按道次排序；手动选择或开赛时随机抓取回写）',
  `developer_controlled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否由开发者指定名次：0=否；1=是',

  `status` VARCHAR(16) NOT NULL DEFAULT 'BETTING' COMMENT '轮次状态：BETTING=竞猜中；RACING=比赛中；PODIUM=领奖台；FINISHED=已结束',
  `bet_start_at` DATETIME NOT NULL COMMENT '竞猜开始时间（其余阶段时间由此 + 本轮时长快照推算）',

  `seed` VARCHAR(32) NULL COMMENT '动画随机种子（hex；演示名次可提前生成，BETTING阶段接口不下发）',
  `result_cipher` TEXT NULL COMMENT '名次结果密文（AES-GCM；仅服务端解密，任何阶段不传输前端）',
  `result_commit` VARCHAR(64) NULL COMMENT '名次承诺（SHA-256(result_json|seed|round_id)，赛后公平性核验）',

  `total_pool` BIGINT NOT NULL DEFAULT 0 COMMENT '本场竞猜总池（=本场全部下注之和）',
  `bet_count` INT NOT NULL DEFAULT 0 COMMENT '下注笔数',
  `paid_total` BIGINT NOT NULL DEFAULT 0 COMMENT '实际发放奖金总额（无人中奖的份额不发放，<=total_pool）',
  `settled_at` DATETIME NULL COMMENT '结算完成时间',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_horse_race_round_no` (`race_id`, `round_no`) COMMENT '同模式内场次序号唯一',
  KEY `idx_horse_race_round_status` (`status`) COMMENT '调度器按状态扫描',
  KEY `idx_horse_race_round_race` (`race_id`, `round_no`) COMMENT '按模式查场次列表',
  CONSTRAINT `fk_horse_race_round_race` FOREIGN KEY (`race_id`) REFERENCES `horse_race` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛马轮次表（每轮一次竞猜+比赛+领奖台）';

-- 下注明细：普通敌人可重复下注、Boss 不可重复下注（服务层校验）
CREATE TABLE `horse_race_bet` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `round_id` BIGINT NOT NULL COMMENT '轮次ID（horse_race_round.id）',
  `race_id` BIGINT NOT NULL COMMENT '模式ID（冗余，便于按模式统计）',
  `user_id` BIGINT NOT NULL COMMENT '下注用户ID（user.id）',
  `asset_id` BIGINT NOT NULL COMMENT '下注对象ID（spine_asset.id）',

  `amount` BIGINT NOT NULL COMMENT '下注金额（龙门币，>=1；单用户单场总额100-3000由服务层校验）',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE=有效；WON=中奖；LOST=未中；REFUNDED=已退款',
  `payout` BIGINT NULL COMMENT '中奖奖金（status=WON 时非空）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下注时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_horse_race_bet_round` (`round_id`, `status`) COMMENT '按轮次结算',
  KEY `idx_horse_race_bet_user` (`user_id`, `round_id`) COMMENT '按用户查单场下注',
  KEY `idx_horse_race_bet_asset` (`round_id`, `asset_id`) COMMENT '按轮次+对象统计奖金池',
  KEY `idx_horse_race_bet_race` (`race_id`) COMMENT '按模式统计',
  CONSTRAINT `fk_horse_race_bet_round` FOREIGN KEY (`round_id`) REFERENCES `horse_race_round` (`id`),
  CONSTRAINT `fk_horse_race_bet_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_horse_race_bet_asset` FOREIGN KEY (`asset_id`) REFERENCES `spine_asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛马下注明细表';

-- 结算台账：每中奖用户每中奖对象一行（双向金额校验依据）
CREATE TABLE `horse_race_settlement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `round_id` BIGINT NOT NULL COMMENT '轮次ID（horse_race_round.id）',
  `user_id` BIGINT NOT NULL COMMENT '中奖用户ID（user.id）',
  `asset_id` BIGINT NOT NULL COMMENT '中奖对象ID（spine_asset.id）',
  `rank_no` TINYINT NOT NULL COMMENT '名次（1/2/3）',

  `bet_amount` BIGINT NOT NULL COMMENT '该用户在该对象上的下注总额',
  `payout` BIGINT NOT NULL COMMENT '实际发放奖金（平分后金额）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结算时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_horse_race_settlement` (`round_id`, `user_id`, `asset_id`) COMMENT '同轮次同用户同对象仅一条',
  KEY `idx_horse_race_settlement_round` (`round_id`) COMMENT '按轮次对账',
  CONSTRAINT `fk_horse_race_settlement_round` FOREIGN KEY (`round_id`) REFERENCES `horse_race_round` (`id`),
  CONSTRAINT `fk_horse_race_settlement_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_horse_race_settlement_asset` FOREIGN KEY (`asset_id`) REFERENCES `spine_asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛马结算台账表';

-- 开发者控制操作审计
CREATE TABLE `horse_race_control_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `race_id` BIGINT NOT NULL COMMENT '模式ID（horse_race.id）',
  `round_id` BIGINT NULL COMMENT '轮次ID（horse_race_round.id；模式级操作可为空）',
  `admin_id` BIGINT NOT NULL COMMENT '操作管理员ID（user.id）',
  `action` VARCHAR(32) NOT NULL COMMENT '操作类型',
  `payload` TEXT NULL COMMENT '操作参数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

  PRIMARY KEY (`id`),
  KEY `idx_horse_race_control_log_race` (`race_id`) COMMENT '按模式查操作记录',
  KEY `idx_horse_race_control_log_round` (`round_id`) COMMENT '按轮次查操作记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛马开发者控制审计表';

SET FOREIGN_KEY_CHECKS = 1;
