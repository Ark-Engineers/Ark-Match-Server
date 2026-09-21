-- 赛马参赛对象改为直接引用 spine_asset：删除 horse_race_participant 表，
-- 轮次以 lineup_json（spine_asset id 数组）保存名单，下注/结算的 participant_id 改为 asset_id。
-- 数据迁移：先建新列 -> 回填 -> 删旧列与外键 -> 删表。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

-- 1. horse_race_round：新增 lineup_json / next_lineup_json
ALTER TABLE `horse_race_round`
    ADD COLUMN `lineup_json` TEXT NULL COMMENT '本轮参赛名单（spine_asset.id 的 JSON 数组，按道次排序）' AFTER `lineup_no`,
    ADD COLUMN `next_lineup_json` TEXT NULL COMMENT '下一轮指定名单（spine_asset.id 的 JSON 数组；NULL 表示沿用本轮）' AFTER `next_lineup_no`;

-- 2. 从参赛者表回填名单 JSON（JSON_ARRAYAGG 不支持内部 ORDER BY，用 GROUP_CONCAT 拼接保证按道次排序）
UPDATE `horse_race_round` r
SET r.lineup_json = (
    SELECT CAST(CONCAT('[', GROUP_CONCAT(p.spine_asset_id ORDER BY p.sort_no SEPARATOR ','), ']') AS JSON)
    FROM `horse_race_participant` p
    WHERE p.race_id = r.race_id AND p.lineup_no = r.lineup_no
);
UPDATE `horse_race_round` r
SET r.next_lineup_json = (
    SELECT CAST(CONCAT('[', GROUP_CONCAT(p.spine_asset_id ORDER BY p.sort_no SEPARATOR ','), ']') AS JSON)
    FROM `horse_race_participant` p
    WHERE p.race_id = r.race_id AND p.lineup_no = r.next_lineup_no
)
WHERE r.next_lineup_no IS NOT NULL;

-- 3. horse_race_bet：participant_id -> asset_id（回填 spine_asset_id）
ALTER TABLE `horse_race_bet`
    ADD COLUMN `asset_id` BIGINT NULL COMMENT '下注对象（spine_asset.id）' AFTER `participant_id`;
UPDATE `horse_race_bet` b
JOIN `horse_race_participant` p ON b.participant_id = p.id
SET b.asset_id = p.spine_asset_id;
ALTER TABLE `horse_race_bet`
    DROP FOREIGN KEY `fk_horse_race_bet_participant`,
    DROP KEY `idx_horse_race_bet_participant`,
    DROP COLUMN `participant_id`,
    MODIFY `asset_id` BIGINT NOT NULL COMMENT '下注对象（spine_asset.id）',
    ADD KEY `idx_horse_race_bet_asset` (`round_id`, `asset_id`) COMMENT '按轮次+对象统计奖金池',
    ADD CONSTRAINT `fk_horse_race_bet_asset` FOREIGN KEY (`asset_id`) REFERENCES `spine_asset` (`id`);

-- 4. horse_race_settlement：participant_id -> asset_id（唯一键随列变化，必须重建为 asset_id 版，
--    否则残留 (round_id, user_id) 唯一键会拦截同轮次同用户多马匹中奖）
ALTER TABLE `horse_race_settlement`
    ADD COLUMN `asset_id` BIGINT NULL COMMENT '中奖对象（spine_asset.id）' AFTER `participant_id`;
UPDATE `horse_race_settlement` s
JOIN `horse_race_participant` p ON s.participant_id = p.id
SET s.asset_id = p.spine_asset_id;
ALTER TABLE `horse_race_settlement`
    DROP FOREIGN KEY `fk_horse_race_settlement_participant`,
    DROP INDEX `uk_horse_race_settlement`,
    DROP COLUMN `participant_id`,
    MODIFY `asset_id` BIGINT NOT NULL COMMENT '中奖对象（spine_asset.id）',
    ADD UNIQUE KEY `uk_horse_race_settlement` (`round_id`, `user_id`, `asset_id`) COMMENT '同轮次同用户同对象仅一条',
    ADD CONSTRAINT `fk_horse_race_settlement_asset` FOREIGN KEY (`asset_id`) REFERENCES `spine_asset` (`id`);

-- 5. 轮次表删除旧列
ALTER TABLE `horse_race_round`
    DROP COLUMN `lineup_no`,
    DROP COLUMN `next_lineup_no`;

-- 6. 删除参赛者表
DROP TABLE IF EXISTS `horse_race_participant`;

SET FOREIGN_KEY_CHECKS = 1;
