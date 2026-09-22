-- 赛马阵容改为每轮开赛时从 spine_asset 随机抓取并回写 id：
-- 轮次以 racer_ids（spine_asset.id 逗号分隔）保存本场参赛名单，废弃 lineup_json / next_lineup_json。
-- 数据迁移：先建新列 -> 从 lineup_json 回填 -> 删旧列。
-- 需停服后执行；仅适用于已执行过 2026-09-22_race_drop_participant(.sql 或 _continue.sql) 的库。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

-- 1. horse_race_round：新增 racer_ids（逗号分隔 id 列表）
ALTER TABLE `horse_race_round`
    ADD COLUMN `racer_ids` VARCHAR(255) NULL COMMENT '本场参赛 spine_asset.id（逗号分隔，开赛时随机抓取回写）' AFTER `next_lineup_json`;

-- 2. 从 lineup_json 回填（JSON 数组如 [1,2,3,4,5] 去括号去空格）
UPDATE `horse_race_round`
SET `racer_ids` = REPLACE(REPLACE(REPLACE(`lineup_json`, '[', ''), ']', ''), ' ', '')
WHERE `lineup_json` IS NOT NULL AND `lineup_json` <> '';

-- 3. 删除旧列
ALTER TABLE `horse_race_round`
    DROP COLUMN `next_lineup_json`,
    DROP COLUMN `lineup_json`;

SET FOREIGN_KEY_CHECKS = 1;
