-- spine_asset 增加赛马参赛统计字段（领奖台阶段写入）
ALTER TABLE spine_asset
    ADD COLUMN race_count INT NOT NULL DEFAULT 0 COMMENT '参赛总场次',
    ADD COLUMN first_place_count INT NOT NULL DEFAULT 0 COMMENT '第一名次数',
    ADD COLUMN second_place_count INT NOT NULL DEFAULT 0 COMMENT '第二名次数',
    ADD COLUMN third_place_count INT NOT NULL DEFAULT 0 COMMENT '第三名次数',
    ADD COLUMN unplaced_count INT NOT NULL DEFAULT 0 COMMENT '未获名次次数（第4-5名）';
