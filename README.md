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

#### 发送重置密码邮箱验证码

- `POST /auth/reset-password/email-code/send`
- 备注：向指定邮箱发送 6 位重置密码验证码（有效期 15 分钟，同一邮箱每小时最多 5 次，同一 IP 每分钟最多 5 次）
- Body：

```json
{
  "email": "user@example.com"
}
```

- Response（`code=0` 表示成功）：

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

#### 重置密码

- `POST /auth/reset-password`
- 备注：通过邮箱验证码重置密码，成功后该用户所有令牌立即失效（令牌版本号 +1）
- Body：

```json
{
  "email": "user@example.com",
  "emailCode": "123456",
  "newPassword": "newPassword123"
}
```

- Response（`code=0` 表示成功）：

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

- 说明：
  - 验证码最多尝试 3 次，超过后自动作废
  - 新密码长度 8-64 位
  - 成功后旧密码和所有已登录会话均失效，需重新登录

### 5.2 业务示例接口（需要登录）

- Admin：
  - `GET /admin/ping`（备注：管理端存活探针；仅 `ADMIN`）
- User：
  - `GET /user/ping`（备注：用户端存活探针；需要登录）

### 5.3 明日方舟绑定接口（需要登录）

- `GET /user/arknights/status`
  - 备注：查询当前用户的明日方舟绑定状态；仅返回当前用户资料；`isAdult` 由 `isMinor` 实时派生。
- `POST /user/arknights/bind`
  - 备注：保存官方授权页回传的明日方舟展示资料；超级管理员不可调用；同一游戏 UID 只能绑定一个本站用户。
  - Body：

```json
{
  "basic": {
    "isMinor": false,
    "hgId": "12345678"
  },
  "accountBinding": {
    "uid": "123456789",
    "nickName": "博士#1234",
    "channelName": "官服"
  }
}
```

  - 关键约束：仅接收 `basic`、`accountBinding` 中的白名单展示字段；禁止提交、记录或返回账号密码、短信验证码、token、cred、signToken、dId 等第三方凭证。
- `POST /user/arknights/unbind`
  - 备注：解除当前用户绑定；超级管理员不可调用；解绑后清空全部明日方舟业务字段。

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
- IP 隐私：业务响应中的 IPv4/IPv6 统一返回 `***`（空值保持不变），管理员及超级管理员也不例外；覆盖 IP 字段、关联 IP 列表、IP 封禁目标、日志/错误文本、CSV 导出及 WebSocket 文本消息。
  - 脱敏仅发生在响应输出边界，数据库、Redis、风控、封禁匹配和审计存储仍使用原始 IP。
  - 关联 IP 封禁按用户 ID 执行，解禁按封禁记录 ID 执行；前端不能将 `***` 作为真实 IP 回传。
- 常用错误码（见 [ErrorCode.java](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/java/io/arknights/dateorfriends/tools/web/ErrorCode.java)）
  - `1000` 参数不合法
  - `1001` 未登录或令牌无效
  - `1002` 无权限访问
  - `2000` 账号不存在
  - `2001` 密码错误
  - `2002` 账号已锁定（连续 5 次失败锁定 15 分钟）
  - `2003` 账号已暂停使用
  - `2004` 账号已封禁
  - `2023` 明日方舟账号已被其他用户绑定
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
| lmd_amount | BIGINT | 附带的龙门币数量（0=纯通知，不携带龙门币） |
| lmd_claim_expire_at | DATETIME NULL | 龙门币领取截止时间（NULL=永久有效；过期后无法领取） |
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
| claimed | TINYINT(1) | 龙门币是否已领取：0 未领取；1 已领取（无龙门币的通知恒为 0） |
| claimed_at | DATETIME NULL | 龙门币领取时间（未领取为 NULL） |
| created_at | DATETIME | 投递时间 |

### 11.11 user_profile 明日方舟绑定字段

- 基线 SQL：[user_profile.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/user_profile.sql)
- 增量 SQL：[arknights_binding.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/arknights_binding.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| arknights_bound | TINYINT(1) | 是否已绑定：0 未绑定；1 已绑定 |
| arknights_is_minor | TINYINT(1) NULL | 官方原始未成年状态：0 成年；1 未成年；NULL 未知 |
| arknights_hg_id | VARCHAR(64) NULL | 森空岛 ID；仅当前绑定用户可查询 |
| arknights_uid | VARCHAR(64) NULL | 明日方舟游戏 UID；以字符串存储，避免精度丢失 |
| arknights_nickname | VARCHAR(128) NULL | 明日方舟游戏昵称（含编号后缀） |
| arknights_channel_name | VARCHAR(64) NULL | 明日方舟区服名称 |
| arknights_bound_at | DATETIME NULL | 最近一次绑定成功时间 |

常用索引（详见 SQL）：
- `uk_user_profile_arknights_uid(arknights_uid)`：保证一个游戏 UID 仅能绑定一个本站用户。
- `idx_user_profile_arknights_bound(arknights_bound)`：按绑定状态筛选。

说明：`isAdult` 不落库，接口响应根据 `!isMinor` 实时派生。

### 11.12 user_wallet（龙门币钱包）

- SQL：[lmd_wallet.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/lmd_wallet.sql)
- 增量 SQL（服务器已有库执行）：[2026-09-20_lmd_wallet.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/incremental/2026-09-20_lmd_wallet.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| user_id | BIGINT | 用户ID（user.id；一人一行） |
| balance | BIGINT | 当前余额（>=0；增减必须同事务写流水） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

常用索引（详见 SQL）：
- `uk_user_wallet_user(user_id)`：保证一个用户仅一条余额记录。

### 11.13 lmd_transaction（龙门币流水，只读追加）

- SQL：[lmd_wallet.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/lmd_wallet.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| user_id | BIGINT | 用户ID（user.id） |
| amount | BIGINT | 变动金额（正=入账，负=出账；不为 0） |
| balance_after | BIGINT | 变动后余额（账面校验依据） |
| type | VARCHAR(32) | 流水类型（MAIL_CLAIM 邮件领取 / ADMIN_ADJUST 管理员调整 / RACE_BET 赛马下注 / RACE_PAYOUT 赛马奖金 / RACE_REFUND 赛马退款） |
| ref_type | VARCHAR(32) NULL | 关联对象类型（如 NOTIFICATION） |
| ref_id | BIGINT NULL | 关联对象ID（如 site_notification.id） |
| description | VARCHAR(255) NULL | 描述（管理员调整时必填原因） |
| trace_id | VARCHAR(64) NULL | 请求溯源ID（X-Trace-Id） |
| request_ip | VARCHAR(64) NULL | 请求来源IP |
| created_by | BIGINT NULL | 操作管理员ID（管理员调整时记录；系统写入为 NULL） |
| created_at | DATETIME | 记账时间 |

常用索引（详见 SQL）：
- `idx_lmd_tx_user_created(user_id, created_at)`：按用户翻页查询流水。
- `idx_lmd_tx_ref(ref_type, ref_id)`：按关联对象查流水。
- `idx_lmd_tx_type(type)`：按类型筛选（审计）。

### 11.14 lmd_mail_claim（龙门币邮件领取记录）

- SQL：[lmd_wallet.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/lmd_wallet.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| notification_id | BIGINT | 通知ID（site_notification.id） |
| user_id | BIGINT | 领取用户ID（user.id） |
| amount | BIGINT | 领取的龙门币数量（快照） |
| trace_id | VARCHAR(64) NULL | 请求溯源ID（X-Trace-Id） |
| request_ip | VARCHAR(64) NULL | 请求来源IP |
| created_at | DATETIME | 领取时间 |

常用索引（详见 SQL）：
- `uk_lmd_mail_claim_notif_user(notification_id, user_id)`：保证同一用户对同一通知仅能领取一次（幂等兜底）。

### 11.15 horse_race（赛马模式实例）

- SQL：[horse_race.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/horse_race.sql)
- 增量 SQL（服务器已有库执行）：[2026-09-20_horse_race.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/incremental/2026-09-20_horse_race.sql)、[2026-09-22_race_drop_participant.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/incremental/2026-09-22_race_drop_participant.sql)（删除参赛者表，参赛对象改为直接引用 spine_asset）、[2026-09-22_race_drop_participant_continue.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/incremental/2026-09-22_race_drop_participant_continue.sql)（断点续跑版：适用于已执行过 09-22 脚本第 1 步的库，含 settlement 唯一键重建修正）、[2026-09-22_spine_race_anim_config.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/incremental/2026-09-22_spine_race_anim_config.sql)（按 skel 实际动画名写入敌人/Boss 待机与移动动画，可重复执行）、[2026-09-22_race_round_racer_ids.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/incremental/2026-09-22_race_round_racer_ids.sql)（阵容改为每轮随机抓取：轮次新增 racer_ids 并回填，删除 lineup_json/next_lineup_json）

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| room_id | VARCHAR(32) | 联机房间ID（online_room.room_id；服务层校验存在性，每房间仅一个 ACTIVE 实例） |
| name | VARCHAR(64) NULL | 模式名称（展示用，可为空） |
| status | VARCHAR(16) | 模式状态：ACTIVE=进行中；CLOSED=已结束/已关闭 |
| session_type | TINYINT | 场次类型：1=一次性；2=限定场次数量；3=无限循环 |
| total_rounds | INT | 总场次数（session_type=1 恒为1；=2 为配置值；=3 恒为0表示不限） |
| participant_mode | TINYINT | 参赛对象生成规则：1=手动选择（全部场次沿用所选 5 名）；2=随机生成（每轮开赛时重新随机） |
| bet_duration_seconds | INT | 单轮竞猜周期（秒；>=60） |
| created_by | BIGINT | 创建人ID（管理员 user.id） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

常用索引（详见 SQL）：
- `idx_horse_race_room(room_id, status)`：按房间+状态查当前实例。
- `idx_horse_race_status(status)`：调度器按状态扫描。

### 11.16 horse_race_round（赛马轮次）

- SQL：[horse_race.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/horse_race.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| race_id | BIGINT | 模式ID（horse_race.id） |
| round_no | INT | 场次序号（从1递增） |
| racer_ids | VARCHAR(255) NULL | 本场参赛 spine_asset.id（逗号分隔，按道次排序）；开赛时按参赛模式（手动选择/随机生成）抓取 5 个 id 回写，参赛对象不建表，动画/缩放随 spine_asset 配置读取 |
| developer_controlled | TINYINT | 是否指定排名的演示场；为1时服务端禁止下注 |
| status | VARCHAR(16) | 轮次状态：BETTING=竞猜中；RACING=比赛中；PODIUM=领奖台；FINISHED=已结束 |
| bet_start_at | DATETIME | 竞猜开始时间 |
| bet_end_at | DATETIME | 竞猜结束时间（比赛前30秒关闭通道：race_start_at=bet_end_at+30秒） |
| race_start_at | DATETIME | 比赛开始时间（前端动画起点，高精度时间戳同步基准） |
| podium_end_at | DATETIME | 领奖台结束时间（初始 race_start_at+120秒；结算后按实际结算时间+60秒） |
| seed | VARCHAR(32) NULL | 动画随机种子（hex；演示名次可提前生成，BETTING 阶段接口不下发） |
| result_cipher | TEXT NULL | 名次结果密文（AES-GCM；仅服务端解密，任何阶段不传输前端） |
| result_commit | VARCHAR(64) NULL | 名次承诺（SHA-256(result_json\|seed\|round_id)，赛后公平性核验） |
| total_pool | BIGINT | 本场竞猜总池（=本场全部下注之和） |
| bet_count | INT | 下注笔数 |
| paid_total | BIGINT | 实际发放奖金总额（无人中奖的份额不发放，<=total_pool） |
| settled_at | DATETIME NULL | 结算完成时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

常用索引（详见 SQL）：
- `uk_horse_race_round_no(race_id, round_no)`：同模式内场次序号唯一。
- `idx_horse_race_round_status(status)`：调度器按状态扫描。

### 11.17 horse_race_bet（赛马下注明细）

- SQL：[horse_race.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/horse_race.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| round_id | BIGINT | 轮次ID（horse_race_round.id） |
| race_id | BIGINT | 模式ID（冗余，便于按模式统计） |
| user_id | BIGINT | 下注用户ID（user.id） |
| asset_id | BIGINT | 下注对象ID（spine_asset.id） |
| amount | BIGINT | 下注金额（龙门币，>=1；单用户单场总额100-3000由服务层校验） |
| status | VARCHAR(16) | 状态：ACTIVE=有效；WON=中奖；LOST=未中；REFUNDED=已退款 |
| payout | BIGINT NULL | 中奖奖金（status=WON 时非空） |
| created_at | DATETIME | 下注时间 |
| updated_at | DATETIME | 更新时间 |

常用索引（详见 SQL）：
- `idx_horse_race_bet_round(round_id, status)`：按轮次结算。
- `idx_horse_race_bet_user(user_id, round_id)`：按用户查单场下注。
- `idx_horse_race_bet_asset(round_id, asset_id)`：按轮次+对象统计奖金池。

### 11.18 horse_race_settlement（赛马结算台账）

- SQL：[horse_race.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/horse_race.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| round_id | BIGINT | 轮次ID（horse_race_round.id） |
| user_id | BIGINT | 中奖用户ID（user.id） |
| asset_id | BIGINT | 中奖对象ID（spine_asset.id） |
| rank_no | TINYINT | 名次（1/2/3） |
| bet_amount | BIGINT | 该用户在该对象上的下注总额 |
| payout | BIGINT | 实际发放奖金（平分后金额） |
| created_at | DATETIME | 结算时间 |

常用索引（详见 SQL）：
- `uk_horse_race_settlement(round_id, user_id, asset_id)`：同轮次同用户同对象仅一条。

### 11.19 horse_race_control_log（赛马控制审计）

- SQL：[horse_race.sql](server/src/main/resources/sql/modules/user/horse_race.sql)
- 增量 SQL：[2026-09-21_horse_race_developer_console.sql](server/src/main/resources/sql/incremental/2026-09-21_horse_race_developer_console.sql)

| 字段 | 类型 | 备注 |
|---|---|---|
| id | BIGINT | 主键，自增 |
| race_id | BIGINT | 模式ID |
| round_id | BIGINT NULL | 轮次ID |
| admin_id | BIGINT | 操作管理员ID |
| action | VARCHAR(32) | 操作类型（SET_RANKING / START_NOW / SETTLE_NOW / END_PODIUM / CLOSE_REFUND） |
| payload | TEXT NULL | 操作参数；排名操作只记录承诺值，不记录明文排名 |
| created_at | DATETIME | 操作时间 |

常用索引（详见 SQL）：
- `idx_horse_race_control_log_race(race_id)`：按模式查询操作记录。
- `idx_horse_race_control_log_round(round_id)`：按轮次查询操作记录。

## 12. 测试说明

- 测试目录：`server/src/test`（依赖 `spring-boot-starter-test`，仅 test scope）
- 纯算法测试（无需数据库，`mvn test -Dtest='RaceSimConsistencyTest,RaceSimulatorRandomTest'`）：
  - `RaceSimConsistencyTest`：Java 与前端 TS 模拟器 100 个种子逐位一致（冲线顺序/冲线tick/采样点 double 精确相等；比对数据由 `arkMatchWeb/scripts/raceSimCrossCheck.mjs` 生成）
  - `RaceSimulatorRandomTest`：120 场拒绝采样必成功且仿真名次与预生成名次一致；100 场速度限定区间与 51~59 秒完赛

## 13. 龙门币系统（LMD）

### 13.1 功能概览

- 每个用户一个钱包（`user_wallet`），余额永不为负；每次余额变动必须同事务写入 `lmd_transaction` 流水（含变动后余额、溯源ID、来源IP）。
- 管理端用户管理（`/admin/m/1`）展示龙门币余额；点击余额或“更多 → 龙门币管理 / 流水”可增加、扣除或设置余额，并按类型分页查看该用户流水。调整需填写原因并二次确认，成功后刷新余额与流水。
- 管理员发布带龙门币的系统通知邮件（`/admin/lmd/mail/publish`），可设置领取截止时间或永久有效；每封邮件每个用户仅可领取一次，领取后自动标记已领取；过期自动失效。
- 用户领取采用“一次性票据”流程：先 `POST /user/lmd/mail/claim-ticket` 换取 Redis 票据（5 分钟有效），再凭票据 `POST /user/lmd/mail/claim` 完成入账；双重请求均限流。
- 账面校验：支持管理员手动全量/单用户校验（余额 vs 流水求和），并有每日凌晨定时自动校验（只告警，不自动修复）。
- 模块解耦：龙门币核心逻辑全部在 `modules/{user,admin}/lmd`，通知模块只负责存储 `lmd_amount / lmd_claim_expire_at / claimed` 字段与广播投递，不含任何龙门币业务规则。

### 13.2 配置

```yaml
app:
  lmd:
    sign-secret: ${LMD_SIGN_SECRET:dev-lmd-sign-secret-change-me}  # 生产必须显式配置，留空则签名接口全部拒绝（fail-closed）
    sign-ts-window-seconds: 300
    verify-cron: "0 30 4 * * ?"
```

### 13.3 用户端接口（/user/lmd，需登录）

- `GET /user/lmd/balance`：查询本人龙门币余额
- `GET /user/lmd/transactions`：查询本人流水（分页，支持按类型筛选）
- `POST /user/lmd/mail/claim-ticket`：为某通知换取一次性领取票据（限流：单用户 20/分钟、单 IP 30/分钟）
- `POST /user/lmd/mail/claim`：凭票据领取龙门币入账（限流：单用户 10/分钟、单 IP 20/分钟）

### 13.4 管理端接口（/admin/lmd，仅 ADMIN/SUPER_ADMIN）

- `GET /admin/lmd/transactions`：全量流水审计（支持 `userId/type/refType/page/size` 筛选分页，含溯源ID与IP；限流 60/分钟）
- `POST /admin/lmd/adjust`：增减用户余额（`userId/amount/description/ts/nonce/sign`；非零整数且单次绝对值不超过 10,000,000；需 HMAC 签名；限流 30/分钟）
- `POST /admin/lmd/set-balance`：设置指定余额（`userId/balance/expectedBalance/description/ts/nonce/sign`；余额为非负安全整数，单次实际变动绝对值不超过 10,000,000 且不可为零；与增减接口共用限流）。事务内锁定钱包并校验 `expectedBalance`，余额已变化则拒绝，由管理员刷新后重新确认；成功写入 `ADMIN_ADJUST` 流水。签名原文为 `lmd.set-balance|userId|balance|expectedBalance|trim(description)|ts|nonce`。
- `POST /admin/lmd/mail/publish`：发布带龙门币的通知邮件（需 HMAC 签名；限流 10/分钟）
- `GET /admin/lmd/mail/claims`：邮件领取记录审计（分页/筛选）
- `GET /admin/lmd/verify`：触发账面校验（全量或指定用户）
- `GET /admin/user-manage/users`、`GET /admin/user-manage/users/{id}`：用户列表和详情增加 `lmdBalance`；未创建钱包的用户余额为 0，列表按当前分页批量查询余额。
- 余额调整权限与用户管理一致：ADMIN 仅能操作 USER，SUPER_ADMIN 可操作 USER/ADMIN；禁止调整超级管理员及已注销账号，查看余额和流水仍允许。

### 13.5 安全机制

- 身份鉴权：`/user/lmd/**` 仅限登录用户本人数据；`/admin/lmd/**` 走全局 `AuthWebFilter` 仅限 ADMIN/SUPER_ADMIN。
- 频率限制：所有 LMD 接口均有 Redis 滑动窗口限流（按用户 / 按 IP 双重维度）。
- 签名校验（管理员写操作）：请求体 + 时间戳 + 随机数 拼接后 HMAC-SHA256 签名，时间戳窗口 ±300s，随机数防重放（Redis SETNX）；生产环境 `LMD_SIGN_SECRET` 未配置时签名校验直接拒绝（fail-closed）。
- 请求溯源：`/user/lmd/**`、`/admin/lmd/**` 请求自动分配 `X-Trace-Id`（TraceWebFilter），流水与领取记录落库 trace_id + 来源 IP，可逐笔追溯。
- 幂等兜底：`lmd_mail_claim` 唯一键（notification_id, user_id）+ 事务内重复领取检测，杜绝并发重复入账。
- 一致性：余额更新、流水插入、领取记录、已领取标记在同一数据库事务（SqlSession 手动事务）内提交，任一失败整体回滚。

## 14. 赛马竞猜系统（Horse Race）

联机房间（/ws/online）内的竞猜玩法：管理员在房间创建赛马模式，房内玩家用龙门币下注 5 名敌人/Boss 参赛者的名次，赛后按位置彩池规则（前三名均中奖，冠军/亚军/季军按 100%/80%/60% 系数派发）自动结算并通知。

### 14.1 功能概览

- 权限与配置：仅管理员可创建/关闭；每房间同一时间仅一个进行中实例；创建时指定竞猜开始时间与颁奖时间（竞猜结束时间由后端反推：颁奖时间 − 30s 预备 − 60s 比赛 − 60s 颁奖，竞猜周期 >= 60 秒）；场次类型三种（一次性/限定场次数量/无限循环）；参赛对象不单独建表，直接引用 spine_asset（敌人/Boss，type 2/3），参赛模式两种——手动选择 5 名（全部场次沿用所选阵容）或随机生成（每轮开赛时重新随机），5 个 id 回写轮次 racer_ids（逗号分隔，按道次排序），展示/下注/结算按 id 回查；待机/移动动画与显示缩放均从明日方舟小人配置读取。
- 下注：仅房间内成员、仅龙门币；单用户单场总额 100~3000，可为不同参赛者分别下注；普通敌人可重复下注，Boss 不可重复下注；比赛开始前 30 秒自动关闭下注通道；总奖池与各参赛对象彩池实时汇总并全房间广播。
- 名次与动画：普通竞猜名次由服务端 `SecureRandom` 独立生成，演示场可由管理员指定；均以 AES-256-GCM 密文落库并附 SHA-256 承诺。下注关闭后下发 8 位 hex 动画种子与开赛时间戳，前端用与后端逐位一致的确定性模拟器（mulberry32 + 拒绝采样，见 `RaceSimulator.java` / 前端 `utils/raceSim.ts`）重建 60 秒动画；正常播放的冲线顺序与存储名次一致，提前结算时直接进入领奖台。
- 结算：位置彩池——前三名均视为中奖，奖池 P 按名次均分三份（除不尽的余数给第一名），各名次份额按系数派发：冠军 100%、亚军 80%、季军 60%，未发放的 20% 系统回收；同马匹注单按赔率比例派奖：`payout = floor(下注额 × 名次奖金 / 该马匹彩池)`，余数 +1 依次给最早的注单；某名次无人押中时其份额按押中马匹彩池加权分摊（最大余数法，并列按名次先后）；前三名全部无人押中且 P>0 时无人中奖，不派发、不退款（注单全部 LOST，奖池系统回收）；第 4/5 名不中奖，其注金留在奖池内参与派发；分配算法见 `RacePlacePool`（结算与双向校验共用同一实现），全部走龙门币整数运算，无浮点误差。
- 赛后：不自动退出场景，广播名次生成领奖台（1/2/3 站位）持续 1 分钟，支持主动提前退出；系统通知每个参与者本人结果与奖金金额，奖金自动入账。
- 双向校验：结算台账（horse_race_settlement）与龙门币流水（RACE_BET/RACE_PAYOUT/RACE_REFUND）逐轮对账，并按位置彩池规则（现行 100%/80%/60% 系数）对已结算、未退款轮次重算逐注派奖比对（历史 60/30/10 等旧规则轮次与退款轮次自动跳过），管理端可手动触发、结算时自动执行。

### 14.2 配置

```yaml
app:
  race:
    secret: ${RACE_SECRET:}          # Base64 编码的 32 字节 AES 密钥；未配置时回退 sha256(app.verify.secret)（仅开发环境）
    secret-required: true            # 生产必须显式配置（fail-closed），缺失时启动失败
    scheduler-enabled: true          # 轮次流转调度器开关
    tick-fixed-delay-ms: 1000        # 调度器扫描间隔
```

### 14.3 用户端接口（/user/online/race，需登录）

- `GET /user/online/race/state?roomId=`：查询房间当前赛马状态（模式/轮次/参赛名单/各参赛对象彩池 horsePools/本人下注 myBets、myTotal/个人结算 myResult（PODIUM/FINISHED 且有下注时返回：payout、betTotal、wins，用于 WS 定向消息丢失后的 HTTP 兜底恢复）/总额区间/服务器时间戳）
- `POST /user/online/race/bet`：下注（body: roomId / assetId / amount；assetId 为 spine_asset.id；Redis 限流 12 次/分钟；校验在房内、阶段 BETTING、总额区间、Boss 去重）

### 14.4 管理端接口（/admin/online/race，仅 ADMIN/SUPER_ADMIN）

- `GET /admin/online/race/catalog`：可参赛对象目录（敌人/Boss spine 资产）
- `GET /admin/online/race/list`：进行中模式概览（含当前轮次与参赛数）
- `POST /admin/online/race/create`：创建（房间/名称/场次类型/场次数/参赛模式/5 名对象/竞猜开始时间 betStartAtMs/颁奖时间 podiumEndAtMs 毫秒时间戳；`participantMode=1` 手动选择必须传 5 个不重复对象 id，全部场次沿用所选阵容；`=2` 随机生成每轮开赛时重新随机；`sessionType=3` 无限循环时无需指定时间，创建后立即开始，单轮竞猜周期默认 120 秒）
- `POST /admin/online/race/{id}/close`：关闭模式（未结算下注自动退款）
- `GET /admin/online/race/{id}`：模式详情（当前参赛名单 + 全部轮次 + `roundParticipants` 按轮次ID索引的历史名单）；管理端可展开轮次查看当时的参赛对象及最终名次。
- `POST /admin/online/race/round/{roundId}/verify`：触发单轮双向台账校验

### 14.5 WebSocket 广播（房间内）

- `race_update`：模式/轮次状态变化，前端收到后重新拉取状态
- `race_pool_update`：奖池实时汇总（roundId / totalPool / horsePools 各参赛对象彩池）
- `race_start`：开赛（roundId / roundNo / seed / raceStartAt / podiumEndAt / developerControlled / durationMs）
- `race_result`：结算结果（roundId / roundNo / ranking 参赛对象ID数组 / totalPool / paidTotal / podiumEndAt / horsePools 各参赛对象最终彩池）
- `race_my_result`：定向推送本人结果（roundId / roundNo / payout / betTotal / wins）
- WS 房主变更 NPE 修复（2026-09-22）：房主断线且房间无其他在线玩家时 `pickNextHost()` 返回 null，`host_change` 与 `snapshot` 广播的 `Map.of` 不允许 null 值抛 NullPointerException（leave 消息、连接清理、房间 ticker 三处）；现无新房东时跳过这两类广播（快照改为等新玩家加入重新指派房东），宽限期淘汰与房间回收不受影响。
- 颁奖阶段数据兜底（2026-09-22）：`race_result` 广播在背压时可能静默丢弃（unicast sink tryEmitNext），且前端原实现忽略消息内 ranking 只依赖 HTTP 刷新，颁奖窗口（数十秒）内请求失败会导致“本场结果”与领奖台空白；现前端收到 `race_result` 直接用消息内 ranking/totalPool/paidTotal/podiumEndAt 进入颁奖阶段（HTTP 轮询仍作后台校正），`/state` 响应新增 `myResult`（个人结算兜底恢复，弥补 `race_my_result` 定向消息丢失），room 层收到 `race_result` 同步刷新本地状态。

### 14.6 安全机制

- 鉴权：`/user/online/race/**` 仅限登录用户本人操作；`/admin/online/race/**` 走全局 `AuthWebFilter` 仅限 ADMIN/SUPER_ADMIN；下注必须为房间在线成员（服务端按 WS 连接校验）。
- 频率限制：下注接口 Redis 滑动窗口限流（单用户 12 次/分钟）。
- 名次保护：名次密文 + SHA-256 承诺在结算前校验，失败拒绝入账；种子可以重建排名，因此仅在下注关闭后下发。指定排名仅允许无人下注的 BETTING 场，设定后服务端禁止下注，并在观赛页面公开标记演示场。
- 密钥容错：展示与开发者控制台读取历史密文解密失败时（如早期用回退密钥加密、后来才写入 `app.race.secret` 的轮次）降级处理——隐藏该轮名次/已存名次并记录 WARN 日志，不返回 500；结算路径不受影响，解密或承诺校验失败仍拒绝入账。
- 一致性：下注（扣款+注单+奖池）、结算（解密+位置彩池派奖+入账+台账+状态）与控制操作均先建立显式 Spring JDBC 事务，再打开 SqlSession，按模式行、轮次行顺序加锁；控制操作绑定 roundId，提前结束还校验 expectedStatus，异常或未提交即回滚，广播在数据库提交后发送。

### 14.7 开发者控制台

- 入口：赛马前端场景顶部显示“开发者控制台”按钮，赛马管理列表也保留入口；仅超级管理员 SUPER_ADMIN 可见和使用，普通管理员与用户不显示。控制台全部读取与写入接口均由后端再次校验 SUPER_ADMIN；普通赛马管理权限不变。所有写操作二次确认并记录管理员、场次与操作时间。
- 安装：已有赛马表的数据库需停服后手动执行一次 `server/src/main/resources/sql/incremental/2026-09-21_horse_race_developer_console.sql`，再启动新版后端；不要执行含 DROP TABLE 的全量建表脚本。
- 2026-09-22 升级（参赛者表下线）：在已执行过上述脚本的库上，停服后按顺序执行 `server/src/main/resources/sql/incremental/2026-09-22_race_drop_participant.sql`（轮次新增 lineup_json/next_lineup_json 并回填、下注与结算 participant_id 改为 asset_id、删除 horse_race_participant 表），再启动新版后端。若该脚本已执行到第 1 步（horse_race_round 已存在 lineup_json）而中断，改执行断点续跑版 `2026-09-22_race_drop_participant_continue.sql`（其余步骤相同，并重建 settlement 唯一键为 asset_id 版）。
- 旧限定场次升级：末轮已有下注或已开赛时，增量脚本仅将其之前无下注、无结果的空占位轮标记结束，保留正在进行的末轮及原下注、名次和结算；全部尚未使用时从第一轮依次进行。历史对象ID不变。
- 阵容改造（2026-09-22，每轮随机抓取）：在已执行过上述脚本的库上，停服后执行 `server/src/main/resources/sql/incremental/2026-09-22_race_round_racer_ids.sql`（轮次新增 racer_ids 并从 lineup_json 回填、删除 lineup_json/next_lineup_json），再启动新版后端；参赛模式恢复两种：`participantMode=1` 手动选择 5 名（创建时指定，全部场次沿用所选阵容）、`=2` 随机生成（每轮开赛时从 spine_asset type 2/3 重新随机抓取），展示/下注/结算按 id 回查 spine_asset（已删除资源跳过，道次顺序即 id 顺序）；随机模式资源池不足 5 个时创建拒绝，轮次切换时沿用本场阵容并记录 WARN。
- 指定下一场恢复（2026-09-22）：开发者控制台恢复”指定下一场参赛名单”功能。需停服后执行 `server/src/main/resources/sql/incremental/2026-09-22_race_next_lineup.sql`（加回 `next_lineup_json` 列），再启动新版后端。优先级：开发者指定 > 手动模式沿用 > 随机模式抓取。
- `GET /admin/online/race/{id}/developer`：返回模式、当前轮次、本场名单、演示场已保存名次、下一场指定名单及是否可安排下一场；普通竞猜不提前返回明文排名。
- `POST /admin/online/race/{id}/developer/start`：Body `{roundId}`；立即截止竞猜并开赛，或跳过开赛前等待；已有下注保留。
- `POST /admin/online/race/{id}/developer/ranking`：Body `{roundId, participantIds}`；按第一至第五名传入本场全部5个不重复对象ID，仅无人下注且尚未开赛时允许，成功后为禁止下注的演示场。
- `POST /admin/online/race/{id}/developer/next-participants`：Body `{assetIds}`；指定下一场 5 名参赛角色（spine_asset.id），传空数组清除指定（恢复按 participantMode 决定）。仅当有下一场时允许。
- `POST /admin/online/race/{id}/developer/end`：Body `{roundId, expectedStatus}`；`RACING` 按已确定排名立即结算，`PODIUM` 跳过领奖台并按场次规则继续或结束；取消竞猜仍使用关闭模式退款接口。
- 角色状态预览：控制台内嵌预览面板与明日方舟小人管理页一致（时装组/动画/设为待机动画/设为移动动画/显示缩放），且可直接修改——动画与缩放保存到 spine_asset 配置（复用 `POST /admin/spine/{id}/update` 部分更新接口，未传字段保持原值），赛道与前端场景下次加载即生效。
- spine 更新 415 修复（2026-09-22）：`POST /admin/spine/{id}/update`（及 import/import-zip）的 displayScale 原以 `@RequestPart Double` 绑定，客户端以非 text/plain 内容类型（如 application/octet-stream）发送该字段时 Spring 找不到解码器直接 415；现改为绑定原始 Part 按 UTF-8 读取后解析（空值保持原值、非法值返回参数错误），任意内容类型均可正常更新。
- 待机/移动动画写入（2026-09-22）：部分敌人 skel 无名为 `Idle`/`Move` 的动画（如 enemy_10001_trslim 为 `Idle_A`/`Move_A`），赛道场景已按 skel 实际动画名智能匹配（支持 `Idle_A`、`Move_Loop` 等后缀，配置缺失或名称不符时退回首个可用动画，加载不再报 "Animation not found"）；已导入资源可在 Navicat 执行 `2026-09-22_spine_race_anim_config.sql` 写入 5 个敌人/Boss 的待机与移动动画，或直接在控制台预览面板用“设为待机动画/设为移动动画”逐个设置。比赛期间 RACING 使用 `move_animation`、BETTING/PODIUM 使用 `idle_animation`，场景每次状态刷新都从接口同步全部参赛者配置（后台修改后即时生效，不再因轮次切换丢失配置退回默认匹配）。
- 自动下一场兜底修复（2026-09-22）：旧限定场次升级后，下一场占位轮若被迁移脚本标记为 FINISHED 或处于非 BETTING 状态，原 `reschedule`（仅限 `status='BETTING' AND bet_count=0`）更新 0 行抛相位错误，轮次卡在 PODIUM、每轮 tick 重复失败无法进入下一场；现改为 `resetEmptyPlaceholder` 复用空占位轮（不限状态，仅限从未开赛、无下注、无结果、无密文的空轮次），并新增兜底：ACTIVE 模式当前轮为 FINISHED（旧数据/迁移遗留）时调度器继续收尾流程开启下一场或关闭模式，`bet_duration_seconds`/`total_rounds` 空值按默认处理。
- 写接口响应使用统一 `{code, message, data}` 格式，成功时 `code=0`、`data=true`；自动流转和手动操作共用结算、退款及台账机制，不直接修改钱包余额。
