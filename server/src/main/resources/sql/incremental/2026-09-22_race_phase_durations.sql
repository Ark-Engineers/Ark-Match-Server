-- module: modules/user/horse_race
-- description: 赛马四阶段可配置时长及轮次时长快照（已有数据库增量迁移）

-- 仅用于已有 horse_race / horse_race_round 的旧库；必须先备份数据库、停服并暂停赛马调度及写入。
-- 本脚本只执行一次（run once），不是幂等脚本；新库使用 modules 全量脚本，不再执行本文件。
-- 先单独运行下方预检并核对结果，再执行 ALTER/UPDATE；MySQL DDL 隐式提交，失败后不可直接重跑整个文件。
-- 仅新增时长配置/快照并删除冗余时间戳列（bet_end_at/race_start_at/podium_end_at）；不修改赛果、下注、结算及审计数据。
-- 服务层校验：竞猜/比赛/领奖台 1..86400 秒，预备 0..86400 秒；旧配置原样保留，不截断或重置。

SET NAMES utf8mb4;
USE `ark_match`;

-- 1. 预检：应仅返回 horse_race.bet_duration_seconds 一行（INT、NOT NULL、DEFAULT 120）。
-- 若缺少该列、定义不符或已有其他时长列，请停止并人工确认迁移状态。
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('horse_race', 'horse_race_round')
  AND COLUMN_NAME IN ('bet_duration_seconds', 'pre_race_duration_seconds',
                      'race_duration_seconds', 'podium_duration_seconds')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 2. 模式配置：已有竞猜时长仅更新备注，新增三个阶段的默认值延续旧行为。
ALTER TABLE `horse_race`
    MODIFY COLUMN `bet_duration_seconds` INT NOT NULL DEFAULT 120 COMMENT '单轮竞猜周期（秒；>=1且<=86400，服务层校验）',
    ADD COLUMN `pre_race_duration_seconds` INT NOT NULL DEFAULT 30 COMMENT '单轮预备时长（秒；>=0且<=86400，服务层校验）' AFTER `bet_duration_seconds`,
    ADD COLUMN `race_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '单轮比赛时长（秒；>=1且<=86400，服务层校验）' AFTER `pre_race_duration_seconds`,
    ADD COLUMN `podium_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '单轮领奖台时长（秒；>=1且<=86400，服务层校验）' AFTER `race_duration_seconds`;

-- 3. 轮次快照：新增四列；旧轮次的预备/比赛/领奖台快照保持原固定值 30/60/60。
-- 同步更新阶段时间列备注，去掉旧硬编码秒数，改为引用本轮可配置时长。
ALTER TABLE `horse_race_round`
    ADD COLUMN `bet_duration_seconds` INT NOT NULL DEFAULT 120 COMMENT '本轮竞猜时长快照（秒；>=1且<=86400，服务层校验）' AFTER `round_no`,
    ADD COLUMN `pre_race_duration_seconds` INT NOT NULL DEFAULT 30 COMMENT '本轮预备时长快照（秒；>=0且<=86400，服务层校验）' AFTER `bet_duration_seconds`,
    ADD COLUMN `race_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '本轮比赛时长快照（秒；>=1且<=86400，服务层校验）' AFTER `pre_race_duration_seconds`,
    ADD COLUMN `podium_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '本轮领奖台时长快照（秒；>=1且<=86400，服务层校验）' AFTER `race_duration_seconds`,
    DROP COLUMN `bet_end_at`,
    DROP COLUMN `race_start_at`,
    DROP COLUMN `podium_end_at`;

-- 4. 所有旧轮次（包括已结束轮次）的竞猜快照取所属模式的旧竞猜配置；缺失父记录或空值时回退 120。
-- 不根据历史时间戳推算时长；显式保留 updated_at，避免 ON UPDATE CURRENT_TIMESTAMP 改写历史更新时间。
UPDATE `horse_race_round` AS rr
LEFT JOIN `horse_race` AS r ON r.`id` = rr.`race_id`
SET rr.`bet_duration_seconds` = COALESCE(r.`bet_duration_seconds`, 120),
    rr.`updated_at` = rr.`updated_at`
WHERE rr.`bet_duration_seconds` <> COALESCE(r.`bet_duration_seconds`, 120);

-- 5. 后检：应返回 8 行，两表各四列均为 INT、NOT NULL，默认值依次为 120/30/60/60。
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('horse_race', 'horse_race_round')
  AND COLUMN_NAME IN ('bet_duration_seconds', 'pre_race_duration_seconds',
                      'race_duration_seconds', 'podium_duration_seconds')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 启服前立即核对：下列两个不匹配计数均应为 0；通过后再启用支持四阶段快照的新服务。
SELECT COUNT(*) AS bet_snapshot_mismatch_count
FROM `horse_race_round` AS rr
LEFT JOIN `horse_race` AS r ON r.`id` = rr.`race_id`
WHERE rr.`bet_duration_seconds` <> COALESCE(r.`bet_duration_seconds`, 120);

SELECT COUNT(*) AS legacy_phase_snapshot_mismatch_count
FROM `horse_race_round`
WHERE `pre_race_duration_seconds` <> 30
   OR `race_duration_seconds` <> 60
   OR `podium_duration_seconds` <> 60;
