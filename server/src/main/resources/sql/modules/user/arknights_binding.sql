-- module: modules/user/arknights_binding
-- description: 为用户个人资料增加明日方舟官方授权绑定展示字段与索引

ALTER TABLE `user_profile`
  ADD COLUMN `arknights_bound` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已绑定明日方舟：0=未绑定；1=已绑定' AFTER `email_enc`,
  ADD COLUMN `arknights_is_minor` TINYINT(1) NULL COMMENT '鹰角原始未成年状态：0=成年；1=未成年；NULL=未知' AFTER `arknights_bound`,
  ADD COLUMN `arknights_hg_id` VARCHAR(64) NULL COMMENT '森空岛ID（仅绑定用户本人可见）' AFTER `arknights_is_minor`,
  ADD COLUMN `arknights_uid` VARCHAR(64) NULL COMMENT '明日方舟游戏UID（字符串，避免精度丢失）' AFTER `arknights_hg_id`,
  ADD COLUMN `arknights_nickname` VARCHAR(128) NULL COMMENT '明日方舟游戏昵称（含编号后缀）' AFTER `arknights_uid`,
  ADD COLUMN `arknights_channel_name` VARCHAR(64) NULL COMMENT '明日方舟区服名称' AFTER `arknights_nickname`,
  ADD COLUMN `arknights_bound_at` DATETIME NULL COMMENT '明日方舟最近一次绑定成功时间' AFTER `arknights_channel_name`,
  ADD UNIQUE KEY `uk_user_profile_arknights_uid` (`arknights_uid`),
  ADD KEY `idx_user_profile_arknights_bound` (`arknights_bound`);
