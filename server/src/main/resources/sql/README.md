# SQL 规范

## 1. 目标

- 所有“建库/建表/新增字段/修改字段/索引变更”等数据库变更，必须以 SQL 文件落地到仓库
- 每个 SQL 文件必须归属到明确模块（通过目录 + 文件头部标识）
- 每个字段必须写备注（MySQL 使用 `COMMENT`）

## 2. 目录结构

```text
server/src/main/resources/sql/
  README.md
  incremental/
    YYYY-MM-DD_<feature>.sql      # 增量脚本：已有库的 ALTER/建表变更，单独成文件，不混入模块全量脚本
  modules/
    admin/
      <feature>.sql
    user/
      <feature>.sql
```

- 全量脚本（`modules/**`）：DROP+CREATE 完整建表，供新库从零初始化（`modules/all_in_one.sql` 汇总）。
- 增量脚本（`incremental/`）：已上线数据库的字段/表变更（ALTER/ADD COLUMN/CREATE TABLE IF NOT EXISTS），文件名带日期；上线时由运维在服务器手动执行一次。

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

- 管理端示例：`modules/admin/ping.sql`
- 用户/管理员账户表示例：`modules/user/users.sql`

