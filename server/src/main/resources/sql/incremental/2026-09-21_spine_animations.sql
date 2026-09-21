-- 为 spine_asset 表增加动画名称字段（用于赛马等场景的动画播放）
ALTER TABLE `spine_asset`
    ADD COLUMN `idle_animation` VARCHAR(64) NULL COMMENT '待机动画名称（如 Idle、Standby）' AFTER `type`,
    ADD COLUMN `move_animation` VARCHAR(64) NULL COMMENT '移动动画名称（如 Move、Walk、Run）' AFTER `idle_animation`,
    ADD COLUMN `display_scale` DECIMAL(5,2) NULL DEFAULT 1.00 COMMENT '显示缩放比例（默认 1.00）' AFTER `move_animation`;
