-- module: modules/user/horse_race
-- description: 联机房间赛马竞猜模式（模式实例、轮次、下注、结算台账、控制审计；参赛对象直接引用 spine_asset）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ark_match`;

DROP TABLE IF EXISTS `horse_race_control_log`;
DROP TABLE IF EXISTS `horse_race_settlement`;
DROP TABLE IF EXISTS `horse_race_bet`;
DROP TABLE IF EXISTS `horse_race_round`;
DROP TABLE IF EXISTS `horse_race`;

-- 赛马模式实例：单个联机房间内仅允许一个 ACTIVE 实例
CREATE TABLE `horse_race` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `room_id` VARCHAR(32) NOT NULL COMMENT '联机房间ID（online_room.room_id；服务层校验存在性）',
  `name` VARCHAR(64) NULL COMMENT '模式名称（展示用，可为空）',

  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '模式状态：ACTIVE=进行中；CLOSED=已结束/已关闭',
  `session_type` TINYINT NOT NULL DEFAULT 1 COMMENT '场次类型：1=一次性；2=限定场次数量；3=无限循环',
  `total_rounds` INT NOT NULL DEFAULT 1 COMMENT '总场次数（session_type=1 恒为1；=2 为配置值；=3 恒为0表示不限）',
  `participant_mode` TINYINT NOT NULL DEFAULT 1 COMMENT '参赛对象生成规则：1=手动选择；2=随机生成（均取自 spine_asset type 2/3）',
  `bet_duration_seconds` INT NOT NULL DEFAULT 120 COMMENT '单轮竞猜周期（秒；>=1且<=86400，服务层校验）',
  `pre_race_duration_seconds` INT NOT NULL DEFAULT 30 COMMENT '单轮预备时长（秒；>=0且<=86400，服务层校验）',
  `race_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '单轮比赛时长（秒；>=1且<=86400，服务层校验）',
  `podium_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '单轮领奖台时长（秒；>=1且<=86400，服务层校验）',

  `created_by` BIGINT NOT NULL COMMENT '创建人ID（管理员 user.id）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_horse_race_room` (`room_id`, `status`) COMMENT '按房间+状态查当前实例',
  KEY `idx_horse_race_status` (`status`) COMMENT '调度器按状态扫描',
  CONSTRAINT `fk_horse_race_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛马模式实例表（每房间一个进行中实例）';

-- 轮次：每轮 = 竞猜 + 预备 + 比赛 + 领奖台；四阶段时长在创建轮次时从模式配置保存为快照
-- 参赛名单不建表：每轮开赛时从 spine_asset（type 2/3）抓取 5 个 id 回写 racer_ids（逗号分隔，按道次排序；手动选择或随机生成），展示/下注/结算按 id 回查 spine_asset
CREATE TABLE `horse_race_round` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `race_id` BIGINT NOT NULL COMMENT '模式ID（horse_race.id）',
  `round_no` INT NOT NULL COMMENT '场次序号（从1递增）',
  `bet_duration_seconds` INT NOT NULL DEFAULT 120 COMMENT '本轮竞猜时长快照（秒；>=1且<=86400，服务层校验）',
  `pre_race_duration_seconds` INT NOT NULL DEFAULT 30 COMMENT '本轮预备时长快照（秒；>=0且<=86400，服务层校验）',
  `race_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '本轮比赛时长快照（秒；>=1且<=86400，服务层校验）',
  `podium_duration_seconds` INT NOT NULL DEFAULT 60 COMMENT '本轮领奖台时长快照（秒；>=1且<=86400，服务层校验）',
  `racer_ids` VARCHAR(255) NULL COMMENT '本场参赛 spine_asset.id（逗号分隔，按道次排序；手动选择或开赛时随机抓取回写）',
  `developer_controlled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否由开发者指定名次：0=否；1=是',

  `status` VARCHAR(16) NOT NULL DEFAULT 'BETTING' COMMENT '轮次状态：BETTING=竞猜中；RACING=比赛中；PODIUM=领奖台；FINISHED=已结束',
  `bet_start_at` DATETIME NOT NULL COMMENT '竞猜开始时间（其余阶段时间由此 + 本轮时长快照推算）',

  `seed` VARCHAR(32) NULL COMMENT '动画随机种子（hex；演示名次可提前生成，BETTING阶段接口不下发）',
  `result_cipher` TEXT NULL COMMENT '名次结果密文（AES-GCM；仅服务端解密，任何阶段不传输前端）',
  `result_commit` VARCHAR(64) NULL COMMENT '名次承诺（SHA-256(result_json|seed|round_id)，赛后公平性核验）',

  `total_pool` BIGINT NOT NULL DEFAULT 0 COMMENT '本场竞猜总池（=本场全部下注之和）',
  `bet_count` INT NOT NULL DEFAULT 0 COMMENT '下注笔数',
  `paid_total` BIGINT NOT NULL DEFAULT 0 COMMENT '实际发放奖金总额（无人中奖的份额不发放，<=total_pool）',
  `settled_at` DATETIME NULL COMMENT '结算完成时间',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_horse_race_round_no` (`race_id`, `round_no`) COMMENT '同模式内场次序号唯一',
  KEY `idx_horse_race_round_status` (`status`) COMMENT '调度器按状态扫描',
  KEY `idx_horse_race_round_race` (`race_id`, `round_no`) COMMENT '按模式查场次列表',
  CONSTRAINT `fk_horse_race_round_race` FOREIGN KEY (`race_id`) REFERENCES `horse_race` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛马轮次表（每轮一次竞猜+比赛+领奖台）';

-- 下注明细：普通敌人可重复下注、Boss 不可重复下注（服务层校验）
CREATE TABLE `horse_race_bet` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `round_id` BIGINT NOT NULL COMMENT '轮次ID（horse_race_round.id）',
  `race_id` BIGINT NOT NULL COMMENT '模式ID（冗余，便于按模式统计）',
  `user_id` BIGINT NOT NULL COMMENT '下注用户ID（user.id）',
  `asset_id` BIGINT NOT NULL COMMENT '下注对象ID（spine_asset.id）',

  `amount` BIGINT NOT NULL COMMENT '下注金额（龙门币，>=1；单用户单场总额100-3000由服务层校验）',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE=有效；WON=中奖；LOST=未中；REFUNDED=已退款',
  `payout` BIGINT NULL COMMENT '中奖奖金（status=WON 时非空）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下注时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_horse_race_bet_round` (`round_id`, `status`) COMMENT '按轮次结算',
  KEY `idx_horse_race_bet_user` (`user_id`, `round_id`) COMMENT '按用户查单场下注',
  KEY `idx_horse_race_bet_asset` (`round_id`, `asset_id`) COMMENT '按轮次+对象统计奖金池',
  KEY `idx_horse_race_bet_race` (`race_id`) COMMENT '按模式统计',
  CONSTRAINT `fk_horse_race_bet_round` FOREIGN KEY (`round_id`) REFERENCES `horse_race_round` (`id`),
  CONSTRAINT `fk_horse_race_bet_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_horse_race_bet_asset` FOREIGN KEY (`asset_id`) REFERENCES `spine_asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛马下注明细表';

-- 结算台账：每中奖用户每中奖对象一行（双向金额校验依据）
CREATE TABLE `horse_race_settlement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `round_id` BIGINT NOT NULL COMMENT '轮次ID（horse_race_round.id）',
  `user_id` BIGINT NOT NULL COMMENT '中奖用户ID（user.id）',
  `asset_id` BIGINT NOT NULL COMMENT '中奖对象ID（spine_asset.id）',
  `rank_no` TINYINT NOT NULL COMMENT '名次（1/2/3）',

  `bet_amount` BIGINT NOT NULL COMMENT '该用户在该对象上的下注总额',
  `payout` BIGINT NOT NULL COMMENT '实际发放奖金（平分后金额）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结算时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_horse_race_settlement` (`round_id`, `user_id`, `asset_id`) COMMENT '同轮次同用户同对象仅一条',
  KEY `idx_horse_race_settlement_round` (`round_id`) COMMENT '按轮次对账',
  CONSTRAINT `fk_horse_race_settlement_round` FOREIGN KEY (`round_id`) REFERENCES `horse_race_round` (`id`),
  CONSTRAINT `fk_horse_race_settlement_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_horse_race_settlement_asset` FOREIGN KEY (`asset_id`) REFERENCES `spine_asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛马结算台账表';

-- 开发者控制操作审计
CREATE TABLE `horse_race_control_log` (
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
