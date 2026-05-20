# dateOrFriends 目录与分层规范

## 1. 目标

- `server` 为唯一可运行模块（唯一启动入口）
- 后端业务代码统一放在一个顶层文件夹：`modules/`
- 在 `modules/` 下按模块分类：`admin`（管理端）、`user`（用户端）
- 后端工具类统一放在一个顶层文件夹：`tools/`（例如启动日志、通用工具、框架适配等）
- 每个“功能大类”独立一个文件夹，并且在该文件夹内部固定采用 `controller / service / mapper` 分层

## 2. 当前目录结构

```text
dateOrFriends/
  pom.xml
  server/
    pom.xml
    src/
      main/
        java/
          io/arknights/dateorfriends/
            DateOrFriendsApplication.java
            tools/
              startup/
                StartupSuccessLogger.java
                AdminBootstrap.java
              jwt/
                JwtProperties.java
                JwtPrincipal.java
                JwtService.java
                JwtTokenType.java
              security/
                AuthWebFilter.java
                PasswordConfig.java
                Role.java
                SecurityProperties.java
                token/
                  RedisTokenStore.java
              softdelete/
                SoftDeletable.java
                SoftDeleteUtils.java
            modules/
              admin/
                auth/
                  controller/
                  service/
                    impl/
                ping/
                  controller/
                  service/
                    impl/
                  mapper/
              user/
                auth/
                  controller/
                  service/
                    impl/
                  mapper/
                ping/
                  controller/
                  service/
                    impl/
                  mapper/
        resources/
          application.yaml
          application-dev.yaml
          application-nodb.yaml
          sql/
            README.md
            modules/
              admin/
                ping.sql
              user/
                users.sql
```

## 3. 包命名约定

- 包结构固定为：`io.arknights.dateorfriends.modules.{模块}.{功能}.{分层}`
- 模块（业务域）：
  - 管理端：`admin`
  - 用户端：`user`
- 分层（固定三层）：
  - `controller`
  - `service`（实现类放在 `service.impl`）
  - `mapper`

### 3.1 示例：Ping 功能

- 管理端（管理员）：
  - `io.arknights.dateorfriends.modules.admin.ping.controller`
  - `io.arknights.dateorfriends.modules.admin.ping.service`
  - `io.arknights.dateorfriends.modules.admin.ping.service.impl`
  - `io.arknights.dateorfriends.modules.admin.ping.mapper`
- 用户端（普通用户）：
  - `io.arknights.dateorfriends.modules.user.ping.controller`
  - `io.arknights.dateorfriends.modules.user.ping.service`
  - `io.arknights.dateorfriends.modules.user.ping.service.impl`
  - `io.arknights.dateorfriends.modules.user.ping.mapper`

## 4. 路由约定

- 管理端接口统一前缀：`/admin/**`
- 用户端接口统一前缀：`/user/**`

## 5. 示例接口（已实现）

### 5.1 Admin Ping

- URL：`GET /admin/ping`
- Controller：`io.arknights.dateorfriends.modules.admin.ping.controller.PingController`
- Service：`io.arknights.dateorfriends.modules.admin.ping.service.PingService`

### 5.2 User Ping

- URL：`GET /user/ping`
- Controller：`io.arknights.dateorfriends.modules.user.ping.controller.PingController`
- Service：`io.arknights.dateorfriends.modules.user.ping.service.PingService`

## 6. 分层职责

- `controller`：只处理请求/参数/返回体，不写业务规则
- `service`：承载业务编排与规则校验
- `mapper`：仅数据库访问（MyBatis Mapper 接口），不写业务逻辑

## 7. Bean 命名约定（重要）

- 因为 `admin` 和 `user` 会大量出现同名类（例如都叫 `PingController`、`LoginServiceImpl`），Spring 默认生成的 beanName 可能冲突（例如都变成 `pingController`）
- 要求：所有 `controller` 与 `service.impl` 必须显式指定唯一 beanName
  - 管理端统一前缀：`admin`
  - 用户端统一前缀：`user`
  - 示例：
    - `@RestController("adminPingController")`
    - `@Service("userPingService")`

## 8. SQL 规范（重要）

- 数据库相关变更（建库/建表/加字段/改字段/索引）必须落到 SQL 文件，不允许只在代码里“口头约定”
- SQL 文件必须按模块归档到：`server/src/main/resources/sql/modules/{admin|user}/`
- 每个 SQL 文件必须包含字段备注（MySQL：列级 `COMMENT` + 表级 `COMMENT`）
- 详细规则见：[sql/README.md](file:///c:/Users/MrLee/Desktop/%E7%BD%97%E5%BE%B7%E4%B9%8B%E9%97%A8/%E7%A8%8B%E5%BA%8F/dateOrFriends/server/src/main/resources/sql/README.md)

## 9. Controller 备注约定（重要）

- 每个 Controller 的每个 API 方法必须写备注（JavaDoc），说明：
  - 接口用途（一句话）
  - 权限要求（例如：仅 ADMIN / 需要登录 / 公开接口）
  - 关键约束（例如：Refresh Token 仅用于刷新，不得用于业务鉴权）
- README 中必须同步维护接口备注（确保文档可独立部署参考）
