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

