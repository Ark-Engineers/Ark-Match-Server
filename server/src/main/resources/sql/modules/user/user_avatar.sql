ALTER TABLE `user`
  ADD COLUMN `avatar_char_id` VARCHAR(64) NULL AFTER `avatar_url`,
  ADD COLUMN `avatar_char_name` VARCHAR(64) NULL AFTER `avatar_char_id`;

