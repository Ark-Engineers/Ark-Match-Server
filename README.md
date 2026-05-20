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
- `ADMIN_ACCOUNT` / `ADMIN_PASSWORD` / `ADMIN_EMAIL` / `ADMIN_NICKNAME`

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

- 服务启动时自动检测系统是否存在管理员账号
- 若不存在，则按配置导入管理员（必须输入账号与密码；BCrypt 加密存储）：
  - 环境变量：`ADMIN_ACCOUNT` / `ADMIN_PASSWORD`（可选 `ADMIN_EMAIL` / `ADMIN_NICKNAME`）
  - 或启动参数：`--app.admin.bootstrap.account=... --app.admin.bootstrap.password=...`
- 初始化逻辑实现：
  - [AdminBootstrap.java](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/java/io/arknights/dateorfriends/tools/startup/AdminBootstrap.java)

## 9. 逻辑删除（Soft Delete）

- 规则：删除不做物理删除，统一通过 `deleted` 字段标记
  - `0`：未删除
  - `1`：已删除
- 工具：
  - [SoftDeletable.java](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/java/io/arknights/dateorfriends/tools/softdelete/SoftDeletable.java)
  - [SoftDeleteUtils.java](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/java/io/arknights/dateorfriends/tools/softdelete/SoftDeleteUtils.java)

## 10. SQL 规范

- 所有建表/加字段/改字段/索引变更必须写 SQL 文件并提交到仓库
- SQL 文件必须绑定模块，并且每个字段必须写备注（MySQL `COMMENT`）
- 规范文档：
  - [sql/README.md](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/README.md)
- 示例 SQL：
  - [ping.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/admin/ping.sql)
  - [users.sql](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/modules/user/users.sql)

## 11. 测试说明

- 按当前约定：项目不提交测试代码与测试依赖
- 如后续需要补齐自动化测试，再新增 `server/src/test` 目录并恢复测试依赖
