# SQL 规范

## 1. 目标

- 所有“建库/建表/新增字段/修改字段/索引变更”等数据库变更，必须以 SQL 文件落地到仓库
- 每个 SQL 文件必须归属到明确模块（通过目录 + 文件头部标识）
- 每个字段必须写备注（MySQL 使用 `COMMENT`）

## 2. 目录结构

```text
server/src/main/resources/sql/
  README.md
  modules/
    admin/
      <feature>.sql
    user/
      <feature>.sql
```

## 3. 文件命名

- 推荐：`<feature>.sql` 或 `<feature>_<purpose>.sql`
- 示例：
  - `modules/admin/ping.sql`
  - `modules/user/account_init.sql`

## 4. 文件头部（必填）

每个 SQL 文件必须以如下两行开头：

```sql
-- module: modules/<module>/<feature>
-- description: <一句话说明本文件用途>
```

## 5. 字段备注（必填）

- 每个字段必须包含 `COMMENT '...'`
- 表必须包含 `COMMENT='...'`
- 逻辑删除字段统一命名：`deleted`
  - `0`：未删除
  - `1`：已删除

## 6. 示例

参考：`modules/admin/ping.sql`

