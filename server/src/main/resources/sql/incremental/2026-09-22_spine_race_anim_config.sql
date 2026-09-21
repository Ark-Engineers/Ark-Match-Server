-- 赛马敌人/Boss 资源的待机与移动动画写入 spine_asset 配置。
-- 依据各资源 skel 内实际动画名（3.8.99 二进制解析结果）：
--   enemy_10001_trslim   : Idle_A/Idle_B、Move_A/Move_B（无 "Idle"）
--   enemy_10003_trwlpl_2 : Idle、Move
--   enemy_1000_gopro     : Idle、Move_Loop（另有 Run_Loop，赛跑用 Move_Loop）
--   enemy_1000_gopro_2   : 同上
--   enemy_1510_frstar2   : Idle、Move
-- 已存在的行会被覆盖为上述值；不存在的 asset_key 不产生任何行，可重复执行。

SET NAMES utf8mb4;

USE `ark_match`;

UPDATE `spine_asset` SET `idle_animation` = 'Idle_A', `move_animation` = 'Move_A' WHERE `asset_key` = 'enemy_10001_trslim';
UPDATE `spine_asset` SET `idle_animation` = 'Idle',   `move_animation` = 'Move'   WHERE `asset_key` = 'enemy_10003_trwlpl_2';
UPDATE `spine_asset` SET `idle_animation` = 'Idle',   `move_animation` = 'Move_Loop' WHERE `asset_key` = 'enemy_1000_gopro';
UPDATE `spine_asset` SET `idle_animation` = 'Idle',   `move_animation` = 'Move_Loop' WHERE `asset_key` = 'enemy_1000_gopro_2';
UPDATE `spine_asset` SET `idle_animation` = 'Idle',   `move_animation` = 'Move'   WHERE `asset_key` = 'enemy_1510_frstar2';
