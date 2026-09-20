-- 增量脚本：明日方舟小人（Spine 资源）增加类型字段
-- 日期：2026-09-20
-- 说明：spine_asset 增加 type 列，用于区分小人类型：1=人物，2=敌人，3=BOSS
-- 执行方式：运维在目标库手动执行一次（存量数据默认归为 1=人物）

USE `ark_match`;

ALTER TABLE `spine_asset`
  ADD COLUMN `type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1=人物，2=敌人，3=BOSS' AFTER `name`;
