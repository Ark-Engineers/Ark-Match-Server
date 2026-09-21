-- module: modules/user/horse_race
-- description: 赛马参赛者表下线迁移·续跑脚本（断点续跑：仅适用于已执行过 2026-09-22_race_drop_participant.sql 第 1 步的库，即 horse_race_round 已存在 lineup_json 列）
-- 执行方式：在 ark_match 库手动执行本文件一次（Navicat 编码选 UTF-8）
-- 较原脚本的修正：settlement 唯一键 uk_horse_race_settlement 原含 participant_id，删列后必须显式重建为 asset_id 版，
-- 否则会残留为 (round_id, user_id) 唯一键，导致同轮次同一用户多马匹中奖（位置彩池允许）被唯一键拦截。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

-- 2. 从参赛者表回填名单 JSON（幂等；JSON_ARRAYAGG 不支持内部 ORDER BY，用 GROUP_CONCAT 拼接保证按道次排序）
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

-- 4. horse_race_settlement：participant_id -> asset_id（唯一键重建为 asset_id 版）
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

-- 保险：开发者控制审计表（若 09-21 脚本未完整执行，此处兜底创建）
CREATE TABLE IF NOT EXISTS `horse_race_control_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `race_id` BIGINT NOT NULL COMMENT '模式ID（horse_race.id）',
  `round_id` BIGINT NULL COMMENT '轮次ID（horse_race_round.id；模式级操作可为空）',
  `admin_id` BIGINT NOT NULL COMMENT '操作管理员ID（user.id）',
  `action` VARCHAR(32) NOT NULL COMMENT '操作类型',
  `payload` TEXT NULL COMMENT '操作参数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

  PRIMARY KEY (`id`),
  KEY `idx_horse_race_control_log_race` (`race_id`) COMMENT '按模式查操作记录',
  KEY `idx_horse_race_control_log_round` (`round_id`) COMMENT '按轮次查操作记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛马开发者控制审计表';

SET FOREIGN_KEY_CHECKS = 1;
