-- module: modules/user/user_profile
-- description: 用户个人信息扩展表（个人主页/资料卡）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `user_profile`;
CREATE TABLE `user_profile` (
  `user_id` BIGINT NOT NULL COMMENT '用户ID（user.id）',

  `featured_role` VARCHAR(32) NULL COMMENT '主推角色（仅允许1个）',
  `signature` VARCHAR(255) NULL COMMENT '个性签名',

  `region_ip` VARCHAR(45) NULL COMMENT '地区来源IP（仅存IP；展示需解析到省市）',

  `birthday` DATE NULL COMMENT '生日（年月日）',
  `birthday_visible` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '生日是否对外公开：0=否；1=是',

  `tags_json` VARCHAR(256) NULL COMMENT '自定义标签JSON数组（最多3个）',

  `contact_pubkey_spki` TEXT NULL COMMENT '联系方式加密公钥（SPKI Base64）',
  `contact_privkey_pkcs8_enc` TEXT NULL COMMENT '联系方式解密私钥（PKCS8 Base64，经服务端主密钥加密存储）',
  `qq_enc` TEXT NULL COMMENT 'QQ密文（Base64）',
  `wechat_enc` TEXT NULL COMMENT '微信密文（Base64）',
  `email_enc` TEXT NULL COMMENT '邮箱密文（Base64）',

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户个人信息扩展表';

SET FOREIGN_KEY_CHECKS = 1;
