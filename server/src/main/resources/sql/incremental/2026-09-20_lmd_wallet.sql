-- module: modules/user/lmd_wallet
-- description: 龙门币系统增量脚本（2026-09-20）：通知表新增龙门币字段 + 新建账户/流水/领取记录表；仅需在已有库执行一次

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

-- ============ 1. site_notification 新增龙门币字段 ============
ALTER TABLE `site_notification`
  ADD COLUMN `lmd_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '龙门币奖励额度（0=无奖励）' AFTER `payload_json`,
  ADD COLUMN `lmd_claim_expire_at` DATETIME NULL COMMENT '龙门币领取截止时间（NULL=永久有效）' AFTER `lmd_amount`;

-- ============ 2. site_notification_user 新增领取状态字段 ============
ALTER TABLE `site_notification_user`
  ADD COLUMN `claimed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已领取龙门币：0 未领取；1 已领取' AFTER `read_at`,
  ADD COLUMN `claimed_at` DATETIME NULL COMMENT '龙门币领取时间（未领取为 NULL）' AFTER `claimed`;

-- ============ 3. 龙门币账户 ============
CREATE TABLE IF NOT EXISTS `user_wallet` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `user_id` BIGINT NOT NULL COMMENT '用户ID（user.id）',
  `balance` BIGINT NOT NULL DEFAULT 0 COMMENT '龙门币余额（非负；账面校验要求等于该用户流水之和）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（首次入账时建行）',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_wallet_user_id` (`user_id`) COMMENT '一个用户一个账户',
  CONSTRAINT `fk_user_wallet_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='龙门币账户';

-- ============ 4. 龙门币流水 ============
CREATE TABLE IF NOT EXISTS `lmd_transaction` (
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

-- ============ 5. 龙门币邮件领取记录 ============
CREATE TABLE IF NOT EXISTS `lmd_mail_claim` (
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
