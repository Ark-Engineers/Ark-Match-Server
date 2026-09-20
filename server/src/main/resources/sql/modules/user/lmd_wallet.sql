-- module: modules/user/lmd_wallet
-- description: 龙门币账户、流水与邮件领取记录（类虚拟货币系统，余额=流水之和强一致）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

-- 龙门币账户：余额唯一权威来源（所有变动必须伴随 lmd_transaction 流水）
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

-- 龙门币流水：每一笔余额变动必须在此留存（记账与账面校验依据）
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

-- 龙门币邮件领取记录：唯一键保证单用户单邮件仅领取一次（幂等防重）
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
