# dateOrFriends

## 1. 简介

- 单体后端（仅 `server` 可启动）
- 业务按模块拆分：`modules/admin`（管理端）、`modules/user`（用户端）
- 工具类统一收敛：`tools/*`
- 数据库变更统一收敛：`server/src/main/resources/sql/*`

## 2. 技术栈

- Java 21
- Spring Boot 4（WebFlux）
- MyBatis + MySQL 8.0
- Redis（令牌黑名单/令牌管理；也可用于 Session）
- JWT（Access/Refresh 双令牌）

## 3. 快速开始

### 3.1 开发环境（dev）

默认启用 `dev` profile：

- 配置文件：[application.yaml](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/application.yaml)
- 开发配置：[application-dev.yaml](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/application-dev.yaml)

需要准备：

- MySQL：默认库名 `ark_match`
  - 初始化 SQL：`server/src/main/resources/sql/modules/user/users.sql`
- Redis：用于令牌黑名单与刷新令牌管理

可通过环境变量覆盖连接信息：

- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE`
- `JWT_SECRET` / `JWT_ISSUER`
- 超级管理员（首次启动自动导入）：`APP_ADMIN_BOOTSTRAP_SUPER_ACCOUNT` / `APP_ADMIN_BOOTSTRAP_SUPER_PASSWORD`（可选 `APP_ADMIN_BOOTSTRAP_SUPER_EMAIL` / `APP_ADMIN_BOOTSTRAP_SUPER_NICKNAME`）
- 管理员（首次启动自动导入）：`APP_ADMIN_BOOTSTRAP_ADMIN_ACCOUNT` / `APP_ADMIN_BOOTSTRAP_ADMIN_PASSWORD`（可选 `APP_ADMIN_BOOTSTRAP_ADMIN_EMAIL` / `APP_ADMIN_BOOTSTRAP_ADMIN_NICKNAME`）

构建：

```bash
./mvnw -DskipTests package
```

运行（示例：随机端口）：

```bash
java -jar server/target/server-0.0.1-SNAPSHOT.jar --server.port=0
```

启动成功会在控制台打印：

- `项目启动成功(Project started)：http://<ip>:<port>`

实现位置：
- [StartupSuccessLogger.java](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/java/io/arknights/dateorfriends/tools/startup/StartupSuccessLogger.java)

## 4. 目录结构（核心约定）

详细规范见：[STRUCTURE.md](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/STRUCTURE.md)

核心规则：

- 业务代码：`io.arknights.dateorfriends.modules.{module}.{feature}.{layer}`
  - module：`admin` / `user`
  - feature：功能大类（一个功能一个文件夹）
  - layer：`controller` / `service` / `mapper`
- 工具代码：`io.arknights.dateorfriends.tools.*`

## 5. 示例接口

### 5.1 认证接口（统一入口，管理员/用户共用）

接口备注约定：

- 每个 API 在 Controller 方法上必须写 JavaDoc 备注（用途/权限/关键约束）
- README 必须同步维护接口备注

#### 登录

- `POST /auth/login`
- 备注：统一登录入口（管理员/普通用户共用），成功后签发 Access/Refresh 双令牌
- Body：

```json
{
  "account": "admin",
  "password": "admin"
}
```

- Response（`code=0` 表示成功）：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "tokenType": "Bearer",
    "accessToken": "...",
    "accessExpiresIn": 7200,
    "refreshToken": "...",
    "refreshExpiresIn": 1296000,
    "userId": 1,
    "role": "ADMIN",
    "weight": 100
  }
}
```

#### 刷新令牌（Refresh Token 仅用于刷新）

- `POST /auth/refresh`
- 备注：仅 Refresh Token 可用；刷新后旧 Refresh 立即作废（Redis + 黑名单）
- Body：

```json
{
  "refreshToken": "..."
}
```

- 说明：
  - 每次刷新会签发新的 Access/Refresh，并作废旧 Refresh（Redis 立即失效 + 黑名单）

#### 登出（单令牌失效）

- `POST /auth/logout`
- 备注：吊销当前 Access Token；可选传 refreshToken 同时吊销该 Refresh
- Header：`Authorization: Bearer <accessToken>`
- Body（可选）：`{"refreshToken":"..."}`（传入则同时作废该刷新令牌）

#### 登出全部（同用户所有令牌立即失效）

- `POST /auth/logout-all`
- 备注：吊销该用户全部端的 Access/Refresh（令牌版本号机制）
- Header：`Authorization: Bearer <accessToken>`
- 说明：通过 Redis “令牌版本号”机制，使历史 Access/Refresh 立即失效

#### 管理员吊销指定用户全部令牌

- `POST /admin/auth/revoke/{userId}`
- 备注：仅 ADMIN 可访问；用于权限变更/风控场景下的全端强制下线
- Header：`Authorization: Bearer <adminAccessToken>`

### 5.2 业务示例接口（需要登录）

- Admin：
  - `GET /admin/ping`（备注：管理端存活探针；仅 `ADMIN`）
- User：
  - `GET /user/ping`（备注：用户端存活探针；需要登录）

## 6. 权限与令牌机制

- Access Token
  - 有效期：2 小时（默认 7200 秒）
  - 用途：所有业务接口鉴权
  - Payload 关键字段：`sub(userId)`、`role`、`weight`、`ver(令牌版本)`、`jti`
- Refresh Token
  - 有效期：15 天（默认 1296000 秒）
  - 用途：仅用于获取新的 Access Token，不可用于业务接口
  - 服务端存储：Redis 以 `auth:refresh:{userId}` 保存合法 Refresh 的 `jti`
- 黑名单
  - Key：`auth:blacklist:{jti}`
  - TTL：与对应令牌剩余有效期一致（登出/刷新/权限变更可立即失效）

## 7. 错误码与返回格式

- 返回结构：`{ code, message, data }`
  - `code=0`：成功
  - `code!=0`：失败
- 常用错误码（见 [ErrorCode.java](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/java/io/arknights/dateorfriends/tools/web/ErrorCode.java)）
  - `1000` 参数不合法
  - `1001` 未登录或令牌无效
  - `1002` 无权限访问
  - `2000` 账号不存在
  - `2001` 密码错误
  - `2002` 账号已锁定（连续 5 次失败锁定 15 分钟）
  - `2003` 账号已暂停使用
  - `2004` 账号已封禁
  - `3001` 令牌已失效（黑名单/版本号变更）

## 8. 默认管理员初始化

- 服务启动时自动检测系统是否存在 `SUPER_ADMIN` 与 `ADMIN`
- 若不存在，则按配置自动导入（账号/密码必填；BCrypt 加密存储；已存在则跳过）
- 推荐配置项（启动参数同名，前缀 `--` 即可）：
  - `app.admin.bootstrap.super-account` / `app.admin.bootstrap.super-password`（可选 `super-email` / `super-nickname`）
  - `app.admin.bootstrap.admin-account` / `app.admin.bootstrap.admin-password`（可选 `admin-email` / `admin-nickname`）
- 备注：为兼容旧配置，`app.admin.bootstrap.account/password/email/nickname` 会作为超级管理员配置的兜底值

示例：

```yaml
app:
  admin:
    bootstrap:
      super-account: superadmin
      super-password: "强密码1"
      super-email: superadmin@example.com
      super-nickname: 超级管理员

      admin-account: admin
      admin-password: "强密码2"
      admin-email: admin@example.com
      admin-nickname: 管理员
```
- 初始化逻辑实现：
  - [AdminBootstrap.java](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/java/io/arknights/dateorfriends/tools/startup/AdminBootstrap.java)


## 9. 逻辑删除（Soft Delete）

- 规则：删除不做物理删除，统一通过 `deleted` 字段标记
  - `0`：未删除
  - `1`：已删除
- 工具：
  - [SoftDeletable.java](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/java/io/arknights/dateorfriends/tools/softdelete/SoftDeletable.java)
  - [SoftDeleteUtils.java](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/java/io/arknights/dateorfriends/tools/softdelete/SoftDeleteUtils.java)

## 10. 管理员封禁系统

### 10.1 功能概览

- IP 封禁：被封禁 IP 无法登录、无法注册、无法访问任何接口（包含受保护资源）。
- 邮箱封禁：被封禁邮箱无法登录、无法注册新账号；若该邮箱已存在账号，会立即失效其所有令牌（强制下线）。
- 用户封禁（按 userId）：封禁后无法访问任何受保护资源，并强制下线；登录/刷新也会被拦截。
- 支持单个封禁与批量封禁、封禁记录查询与 CSV 导出、到期自动解封（定时任务）。
- 白名单：白名单内 IP/邮箱禁止被封禁（默认包含 127.0.0.1 / ::1，以及开发环境 admin@admin.com）。

### 10.2 配置

开发环境配置文件：`server/src/main/resources/application-dev.yaml`

```yaml
app:
  security:
    ban:
      min-admin-weight: 100
      whitelist-ips:
        - 127.0.0.1
        - ::1
      whitelist-emails:
        - admin@admin.com
      expire-sweep-fixed-delay-ms: 60000
      sync-active-bans-fixed-delay-ms: 300000
```

说明：

- `/admin/**` 仅管理员可访问；封禁相关接口额外要求管理员 `weight >= min-admin-weight`。
- IP 封禁通过全局 WebFilter 拦截，覆盖 `/auth/login`、`/auth/register`、`/auth/refresh` 及全部业务接口。

### 10.3 管理端封禁接口（/admin/ban）

- `POST /admin/ban/ip`：封禁单个 IP
- `POST /admin/ban/ip/batch`：批量封禁 IP（必须 confirm=true）
- `POST /admin/ban/email`：封禁单个邮箱
- `POST /admin/ban/email/batch`：批量封禁邮箱（必须 confirm=true）
- `POST /admin/ban/user`：封禁单个用户（按 userId）
- `POST /admin/ban/user/batch`：批量封禁用户（按 userId；必须 confirm=true）
- `POST /admin/ban/unban`：按 recordId 解封
- `GET /admin/ban/records`：封禁记录查询（分页/筛选）
- `GET /admin/ban/records/export`：导出封禁记录 CSV

### 10.4 用户查询与三类封禁（/admin/user）

- `GET /admin/user/search`：按 userId/account/email/ip 检索用户；返回用户基础信息 + 关联IP列表
- `POST /admin/user/ban/ip-only`：仅封禁目标用户关联IP（不修改账号状态）
- `POST /admin/user/ban/email-only`：仅封禁目标用户绑定邮箱（并强制下线）
- `POST /admin/user/ban/full`：全封禁（关联IP + 邮箱 + 关联账号；必须 confirm=true）

## 10. SQL 规范

- 所有建表/加字段/改字段/索引变更必须写 SQL 文件并提交到仓库
- SQL 文件必须绑定模块，并且每个字段必须写备注（MySQL `COMMENT`）
- 规范文档：
  - [sql/README.md](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/README.md)
- 示例 SQL：
  - [ping.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/admin/ping.sql)
  - [users.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/users.sql)

## 11. 数据字典（数据库备注同步）

本节用于把“数据库字段 COMMENT”同步到文档，便于前后端/测试/运维对齐口径。若字段变更，必须同时更新对应 SQL 与本节。

### 11.1 user（用户表，含管理员）

- SQL：`server/src/main/resources/sql/modules/user/users.sql`
- 关键枚举：
  - `role`：`USER` / `ADMIN` / `SUPER_ADMIN`
  - `status`：`NORMAL` / `SUSPENDED` / `BANNED`

### 11.2 ban_record（封禁记录表）

- SQL：[ban.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/admin/ban.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| target_type | ENUM(IP,EMAIL,USER) | 封禁目标类型 |
| target_value | VARCHAR(255) | 封禁目标值（IP/邮箱/用户ID） |
| banned_user_id | BIGINT NULL | 被封禁用户ID（user.id；IP/EMAIL 可能为空） |
| report_id | BIGINT NULL | 关联举报单ID（预留；当前不启用） |
| admin_id | BIGINT | 操作管理员ID（user.id） |
| reason | VARCHAR(255) NULL | 封禁原因 |
| duration_seconds | BIGINT NULL | 封禁时长（秒；NULL=永久） |
| effective_at | DATETIME | 封禁生效时间 |
| expires_at | DATETIME NULL | 封禁到期时间（NULL=永久） |
| status | ENUM(ACTIVE,EXPIRED,REVOKED) | 状态：生效中/到期解封/提前解封 |
| unbanned_at | DATETIME NULL | 解封时间（手动/自动） |
| unbanned_by | BIGINT NULL | 手动解封管理员ID（自动解封为 NULL） |
| unban_type | ENUM(AUTO,MANUAL) NULL | 解封类型：AUTO 自动；MANUAL 手动 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

常用索引（详见 SQL）：
- `idx_ban_record_target(target_type,target_value)`：按目标查询
- `idx_ban_record_banned_user_id(banned_user_id)`：按被封用户查询
- `idx_ban_record_status(status)`：按状态查询
- `ft_ban_record_keyword(target_value,reason)`：关键词全文索引

### 11.3 ban_operation_log（封禁操作日志，只读追加）

- SQL：[ban.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/admin/ban.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| record_id | BIGINT | 封禁记录ID（ban_record.id） |
| actor_id | BIGINT NULL | 操作人ID（管理员 user.id；系统自动为 NULL） |
| actor_role | VARCHAR(32) NULL | 操作人角色（ADMIN 等；系统自动为 NULL） |
| action_type | ENUM(BAN,UNBAN_MANUAL,UNBAN_AUTO) | 操作类型 |
| from_status | ENUM(ACTIVE,EXPIRED,REVOKED) NULL | 变更前状态（BAN 时为 NULL） |
| to_status | ENUM(ACTIVE,EXPIRED,REVOKED) | 变更后状态 |
| created_at | DATETIME | 操作时间 |

### 11.4 admin_role_operation_log（管理员角色变更日志，只读追加）

- SQL：[permission.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/admin/permission.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| actor_id | BIGINT NULL | 操作人ID（user.id；系统自动为 NULL） |
| actor_role | VARCHAR(32) | 操作人角色（当前仅 SUPER_ADMIN） |
| target_user_id | BIGINT | 被授权/撤销的目标用户ID（user.id） |
| action_type | VARCHAR(32) | 操作类型：GRANT_ADMIN / REVOKE_ADMIN |
| from_role | VARCHAR(32) | 变更前角色（USER/ADMIN/SUPER_ADMIN） |
| to_role | VARCHAR(32) | 变更后角色（USER/ADMIN/SUPER_ADMIN） |
| created_at | DATETIME | 操作时间 |

### 11.5 notice（公告表）

- SQL：[notice.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/admin/notice.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| title | VARCHAR(128) | 标题 |
| content | TEXT | 内容（按原样展示） |
| level | ENUM(NORMAL,IMPORTANT) | 等级：普通/重要（可强提醒） |
| status | ENUM(DRAFT,PUBLISHED,OFFLINE) | 状态：草稿/已发布/已下线 |
| pinned | TINYINT(1) | 是否置顶：0 否；1 是 |
| publish_at | DATETIME NULL | 发布时间（未发布为 NULL） |
| expire_at | DATETIME NULL | 过期时间（NULL=不过期） |
| created_by | BIGINT | 创建人ID（user.id） |
| updated_by | BIGINT | 最后修改人ID（user.id） |
| deleted | TINYINT(1) | 软删除标记：0 否；1 是 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

常用索引（详见 SQL）：
- `idx_notice_status(status)`：按状态查询
- `idx_notice_level(level)`：按等级查询
- `idx_notice_pinned(pinned)`：按置顶查询
- `idx_notice_publish_at(publish_at)`：按发布时间查询
- `ft_notice_keyword(title,content)`：关键词全文索引

### 11.6 notice_read（公告已读记录，只读追加）

- SQL：[notice.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/admin/notice.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| notice_id | BIGINT | 公告ID（notice.id） |
| user_id | BIGINT | 用户ID（user.id） |
| read_at | DATETIME | 阅读时间 |

### 11.7 notice_operation_log（公告操作审计日志，只读追加）

- SQL：[notice.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/admin/notice.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| notice_id | BIGINT | 公告ID（notice.id） |
| actor_id | BIGINT NULL | 操作人ID（管理员 user.id；系统自动为 NULL） |
| actor_role | VARCHAR(32) NULL | 操作人角色（ADMIN 等） |
| action_type | ENUM(CREATE,UPDATE,PUBLISH,OFFLINE,DELETE) | 操作类型 |
| ip | VARCHAR(64) NULL | 客户端IP（基于请求头解析） |
| detail | VARCHAR(512) NULL | 操作说明（简要描述） |
| created_at | DATETIME | 操作时间 |

### 11.8 user_manage_operation_log（用户管理操作审计日志，只读追加）

- SQL：[user_manage.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/admin/user_manage.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| actor_id | BIGINT NULL | 操作人ID（管理员 user.id；系统自动为 NULL） |
| actor_role | VARCHAR(32) NULL | 操作人角色（ADMIN/SUPER_ADMIN 等；系统自动为 NULL） |
| target_user_id | BIGINT | 被操作的目标用户ID（user.id） |
| action_type | VARCHAR(64) | 操作类型（如 UPDATE_PROFILE/RESET_PASSWORD/DEACTIVATE/BAN/UNBAN/GRANT_ADMIN/REVOKE_ADMIN 等） |
| ip | VARCHAR(64) NULL | 客户端IP（基于请求头解析） |
| detail | VARCHAR(512) NULL | 简要说明（便于后台直接展示） |
| diff_json | JSON NULL | 字段级变更详情（before/after） |
| created_at | DATETIME | 操作时间 |

### 11.9 site_notification（站内通知主体）

- SQL：[notification.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/notification.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| type | VARCHAR(64) | 通知类型（如 SYSTEM/SECURITY/ACCOUNT 等） |
| title | VARCHAR(128) | 标题 |
| content | TEXT | 内容（按原样展示） |
| level | ENUM(NORMAL,IMPORTANT) | 等级：普通/重要 |
| link_url | VARCHAR(512) NULL | 跳转链接（站内路由/外链） |
| payload_json | JSON NULL | 结构化扩展数据 |
| status | ENUM(SENT,OFFLINE) | 状态：已发送/下线 |
| expire_at | DATETIME NULL | 过期时间（NULL=不过期） |
| created_by | BIGINT NULL | 创建人ID（管理员 user.id；系统创建为 NULL） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 11.10 site_notification_user（站内通知投递与已读状态）

- SQL：[notification.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/notification.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| notification_id | BIGINT | 通知ID（site_notification.id） |
| user_id | BIGINT | 接收用户ID（user.id） |
| read | TINYINT(1) | 是否已读：0 未读；1 已读 |
| read_at | DATETIME NULL | 阅读时间（未读为 NULL） |
| created_at | DATETIME | 投递时间 |

## 12. 测试说明

- 按当前约定：项目不提交测试代码与测试依赖
- 如后续需要补齐自动化测试，再新增 `server/src/test` 目录并恢复测试依赖
