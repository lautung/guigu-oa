# 本地启动与数据库

目标产品是**统一客户端** `client/guigu-oa-client`（Vue 3.5 + Vite 8 + Element Plus + Pinia）配合后端 `server/lautung-oa-parent`（多模块 Maven；Spring Boot 4.1.0、JDK 21、Flowable 8.0.0 Process）。旧的 `管理端`/`员工端`/`其他` 前端不是目标产品。系统依赖（Maven、JDK 21、MySQL 8、Redis）已装入环境镜像；update 脚本只刷新 `npm install` 与 `mvn -DskipTests install`。

测试命令见 `../agents/testing.md`；Compose 演示栈见 `compose-demo.md`。

## 服务与端口

| 服务 | 位置 | 命令 | 端口 |
|---|---|---|---|
| 后端 lautung-oa-server | `server/lautung-oa-parent/lautung-oa-server` | 见下方启动说明 | 8800 |
| 前端统一客户端 | `client/guigu-oa-client` | `npm run dev` | 9530 |
| MySQL | 系统服务 | `sudo service mysql start` | 3306 |
| Redis | 系统服务 | `sudo service redis-server start` | 6379 |

前端 Vite 把 `/dev-api` 代理到 `http://localhost:8800`，所以后端必须在 8800 运行。客户端可见前缀仍是 `/dev-api`（`VITE_APP_BASE_API`）。

Compose 项目名随工作目录（worktree 下即目录名），接手他人在跑的栈先 `docker compose ls` 盘点再按项目 down；`perf/ops/restore.sh`/`snapshot.sh` 硬编码 `guigu-oa_*` 卷名，非主仓目录起栈要带 `COMPOSE_PROJECT_NAME=guigu-oa`，否则快照恢复静默落空。

## 后端启动

1. **用 JDK 21 编译和运行**（`java.version` / `--release` 21）。
2. `lautung-oa-server` 的 `pom.xml` 已把 `src/main/resources/**` 打进 jar。在 `lautung-oa-server` 目录先 `mvn -DskipTests install`，再：
   `java -jar target/lautung-oa-server.jar`
   若本地 jar 仍读不到配置，可临时加 `--spring.config.additional-location=file:./src/main/resources/`。
3. Flowable 的 `ACT_*` 表由引擎按 `application-dev.yml` 的 `flowable.database-schema-update: true`、`flowable.history-level: full` 维护，空库首启动自动建表。坑史：Connector/J 8.x 起 `nullCatalogMeansCurrent` 默认 false，同服务器另一个库有 ACT_ 表时空库会被误判「引擎表已建」、误走 upgrade 链启动失败（#158）——已在 base `application.yml` 的 Hikari `data-source-properties` 钉回「当前库」语义，对所有 profile 与 URL 覆盖生效，删属性即复发。本地起真实服务验证：连已有库 + `--spring.flyway.validate-on-migrate=false`，配方见 `../agents/testing.md` 服务端节。从 Activiti 7 切库见 `server/lautung-oa-parent/sql/flowable8-cutover.md`。
4. OpenAPI / Swagger UI：只在 `dev` profile 下无需登录可读（`/v3/api-docs`、`/swagger-ui.html`）。Compose 演示栈（`dev,demo`）与 `prod` 都关闭这两个端点（#140）。界面入口在后台「系统工具 → 接口文档」（`/tool/api-doc`，#246）：页面挂载探测 `/v3/api-docs`，可用时给「打开 Swagger UI」按钮，未启用时只显示空态；Vite 与 nginx 为此各加了 `/v3`、`/swagger-ui` 两段同源转发。业务接口仍用请求头 `token`。
5. Compose 构建：本机 `docker compose build lautung-oa-server` 直连 Maven Central 会被代理打断 TLS 握手，需带镜像源 `MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose build lautung-oa-server`。
6. 来源地址与 `TRUSTED_PROXIES`（#262，ADR 0063）：本地直连后端默认不信任 `X-Real-IP` / `X-Forwarded-For`，限流与操作日志只认 socket 对端。要从本机模拟受信代理（例如手测限流按来源区分），启动前显式 `TRUSTED_PROXIES=127.0.0.1/32`；Compose 演示栈的配置见 `compose-demo.md`。

## 数据库初始化

后端连接 MySQL 库 `guigu-oa`（`root`/`root`）。库不存在时先创建空库：

```sh
mysql -uroot -proot -h127.0.0.1 -e 'CREATE DATABASE IF NOT EXISTS `guigu-oa` CHARACTER SET utf8mb4;'
```

首次启动 `lautung-oa-server` 时，Flyway 自动创建业务 `sys_*` / `oa_*` 表并写入基础种子，迁移位于 `server/lautung-oa-parent/lautung-oa-server/src/main/resources/db/migration/`；`lautung-oa-server/src/main/java/db/migration/` 下还有 Java 迁移（如 V104），选下一个版本号时两个目录一起看。这是空库启动路径；由旧初始化脚本创建的现有库不会自动 baseline，切换前需另行安排 baseline。`ACT_*` / `FLW_*` 引擎表仍由 Flowable 根据 `application-dev.yml` 自动创建（空库首启动的当前限制见「后端启动」第 3 条与 #158）。

种子登录账号：**用户名 `admin` / 密码 `111111`**（初始密码使用 BCrypt；旧 MD5 密码会在首次成功登录后升级为 BCrypt；`admin` 通过种子角色「配置管理员」获得配置菜单权限，`userId=1` 不再特殊）。配置类接口由服务端 `configurationMenuGate` 按菜单授予统一判定；`user` 种子没有配置角色，只用于办事。生产环境需启用 `prod` profile，并通过 `JWT_SECRET` 注入至少 64 字节的 JWT 签名密钥。
