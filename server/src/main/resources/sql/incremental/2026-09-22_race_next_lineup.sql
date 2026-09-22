-- 开发者控制台恢复"指定下一场参赛名单"功能：
-- 重新加回 next_lineup_json 列（spine_asset.id JSON 数组，按道次排序）。
-- 仅作用于下一场切换，不修改本场及历史；空数组清除计划。
-- 可重复执行（IF NOT EXISTS 语义通过存储过程兜底）。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

-- 存储过程：条件执行 DDL
DROP PROCEDURE IF EXISTS exec_if;
DELIMITER //
CREATE PROCEDURE exec_if(IN ddl TEXT)
BEGIN
    IF ddl IS NOT NULL AND ddl <> '' AND ddl <> 'SELECT 1' THEN
        SET @ddl = ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- 加回 next_lineup_json 列（如已存在则跳过）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'horse_race_round' AND COLUMN_NAME = 'next_lineup_json');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `horse_race_round` ADD COLUMN `next_lineup_json` TEXT NULL COMMENT ''下一轮指定参赛名单（spine_asset.id JSON 数组；NULL=沿用本轮）'' AFTER `racer_ids`',
    'SELECT 1');
CALL exec_if(@sql);

DROP PROCEDURE IF EXISTS exec_if;

SET FOREIGN_KEY_CHECKS = 1;
