-- 2026-09-23: 联机公屏聊天记录表
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `room_id`    VARCHAR(32)  NOT NULL COMMENT '房间 ID',
  `user_id`    BIGINT       NOT NULL COMMENT '发送者用户 ID',
  `nickname`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '发送时昵称（已过滤）',
  `content`    VARCHAR(500) NOT NULL COMMENT '消息内容（已过滤）',
  `sender_ip`  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '发送者 IP（已打星号）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_room_created` (`room_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='联机公屏聊天记录';

-- 2026-09-23: 屏蔽词库表
CREATE TABLE IF NOT EXISTS `profanity_word` (
  `id`         INT          NOT NULL AUTO_INCREMENT,
  `word`       VARCHAR(64)  NOT NULL COMMENT '屏蔽词',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='屏蔽词库';
