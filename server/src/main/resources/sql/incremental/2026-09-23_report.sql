-- 2026-09-23: 举报记录表
CREATE TABLE IF NOT EXISTS `report` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `reporter_user_id` BIGINT       NOT NULL COMMENT '举报人用户ID',
  `reported_user_id` BIGINT       NOT NULL COMMENT '被举报人用户ID',
  `report_type`      ENUM('NICKNAME','SIGNATURE','CHAT') NOT NULL COMMENT '举报类型：昵称/签名/聊天记录',
  `content`          VARCHAR(500) NOT NULL COMMENT '举报内容（被举报的具体文字）',
  `room_id`          VARCHAR(32)  NULL COMMENT '房间ID（聊天举报时记录）',
  `chat_message_id`  BIGINT       NULL COMMENT '关联聊天消息ID（聊天举报时记录）',
  `status`           ENUM('PENDING','HANDLED','DISMISSED') NOT NULL DEFAULT 'PENDING' COMMENT '状态：待处理/已处理/已驳回',
  `handled_by`       BIGINT       NULL COMMENT '处理管理员ID',
  `handled_at`       DATETIME     NULL COMMENT '处理时间',
  `action_taken`     VARCHAR(255) NULL COMMENT '处理动作（RESET_NICKNAME/RESET_SIGNATURE/BAN_USER/BAN_IP/DISMISSED等，可组合逗号分隔）',
  `action_detail`    TEXT         NULL COMMENT '处理详情JSON',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_report_reporter` (`reporter_user_id`),
  INDEX `idx_report_reported` (`reported_user_id`),
  INDEX `idx_report_status` (`status`),
  INDEX `idx_report_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报记录表';
