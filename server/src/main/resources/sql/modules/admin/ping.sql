-- module: modules/admin/ping
-- description: Ping 示例表（演示建表/字段备注/逻辑删除）

CREATE TABLE IF NOT EXISTS `admin_ping_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message` VARCHAR(255) NOT NULL COMMENT '消息内容',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除(0未删除,1已删除)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端Ping日志表';

