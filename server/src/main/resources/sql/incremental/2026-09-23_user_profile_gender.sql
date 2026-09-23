-- 2026-09-23: user_profile 增加 gender 字段
ALTER TABLE `user_profile`
  ADD COLUMN `gender` VARCHAR(10) NULL COMMENT '性别：MALE/FEMALE；NULL=未设置'
  AFTER `signature`;
