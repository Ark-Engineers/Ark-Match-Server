-- module: modules/user/horse_race
-- description: 赛马开发者控制台增量（不可变名单版本、轮次控制字段、操作审计）
-- 执行方式：停服后在目标库手动执行一次，再启动新版后端；旧名单与轮次默认版本为1，保留原对象ID及下注、密文引用

SET NAMES utf8mb4;

USE `ark_match`;

ALTER TABLE `horse_race_participant`
  ADD COLUMN `lineup_no` INT NOT NULL DEFAULT 1 COMMENT '名单版本号（同模式内从1递增）' AFTER `race_id`,
  DROP INDEX `uk_horse_race_participant_race_sort`,
  DROP INDEX `uk_horse_race_participant_race_asset`,
  ADD UNIQUE KEY `uk_horse_race_participant_race_sort` (`race_id`, `lineup_no`, `sort_no`) COMMENT '同模式同名单内道次唯一',
  ADD UNIQUE KEY `uk_horse_race_participant_race_asset` (`race_id`, `lineup_no`, `spine_asset_id`) COMMENT '同模式同名单内参赛对象不重复';

ALTER TABLE `horse_race_round`
  ADD COLUMN `lineup_no` INT NOT NULL DEFAULT 1 COMMENT '本轮名单版本号' AFTER `round_no`,
  ADD COLUMN `next_lineup_no` INT NULL COMMENT '下一轮指定名单版本号（NULL表示未指定）' AFTER `lineup_no`,
  ADD COLUMN `developer_controlled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否由开发者指定名次：0=否；1=是' AFTER `next_lineup_no`,
  MODIFY COLUMN `seed` VARCHAR(32) NULL COMMENT '动画随机种子（hex；演示名次可提前生成，BETTING阶段接口不下发）',
  MODIFY COLUMN `podium_end_at` DATETIME NOT NULL COMMENT '领奖台结束时间（初始race_start_at+120秒，结算后按实际结算时间+60秒）';

-- 旧限定模式曾直接使用预建末轮；保留已有竞猜进度，仅结束末轮之前无下注、无结果的空占位轮次。
UPDATE `horse_race_round` skipped
JOIN `horse_race` race ON race.id=skipped.race_id
JOIN `horse_race_round` current_round
  ON current_round.race_id=race.id AND current_round.round_no=race.total_rounds
SET skipped.status='FINISHED'
WHERE race.status='ACTIVE' AND race.session_type=2
  AND skipped.round_no<current_round.round_no
  AND (current_round.status IN ('RACING','PODIUM')
       OR current_round.bet_count>0
       OR EXISTS (SELECT 1 FROM `horse_race_bet` b WHERE b.round_id=current_round.id))
  AND skipped.status='BETTING' AND skipped.bet_count=0 AND skipped.total_pool=0
  AND skipped.paid_total=0 AND skipped.settled_at IS NULL
  AND skipped.seed IS NULL AND skipped.result_cipher IS NULL AND skipped.result_commit IS NULL
  AND NOT EXISTS (SELECT 1 FROM `horse_race_bet` b WHERE b.round_id=skipped.id);

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
