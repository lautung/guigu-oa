## Agent skills

Per-repo configuration for [Matt Pocock skills 1.3](https://github.com/mattpocock/skills/releases/tag/v1.3.1).

### Pull requests

创建或更新 PR（包括草稿 PR）前，必须读取并使用 Matt Pocock 的 `pr` skill（在当前可用 skills 中定位 `pr/SKILL.md`），按其模板编写 PR 正文。

### Commits

一个 ADR / issue 完成即提交，不把两轮改动留在同一工作区：收尾时 `git status` 只应剩一个主题的改动。混在一起后只能按 hunk 手工拆分，拆分本身会静默丢内容。

### Issue tracker

Issues live in this repo's GitHub Issues (via the `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical roles map 1:1 to tracker labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

从 `needs-triage` 直接实现一个 issue 时，先用 `file:line` 对着代码核验它的事实主张，再定方案——主张可能就是错的（#156 写「空列表返回 200」，实际早已是 201 失败）。

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. Before exploring the codebase or naming domain concepts, read `docs/agents/domain.md`.

### Module map

`docs/agents/client-modules.md` indexes the client modules and server approval ports with their interfaces; read it before grepping for a seam. `docs/agents/server-modules.md` maps the server modules (where `Result`/异常处理/安全过滤链/权限失效 live) and the request-rejection order of the security chain.

侦察成本：一次派一个事实域（DDL、客户端载荷、service 行为分开问），报告只要结论 + `file:line`；要改的文件自己读。

## 项目环境与运行

目标产品是**统一客户端** `client/guigu-oa-client`（Vue 3.5 + Vite 8 + Element Plus + Pinia）配合后端 `server/guigu-oa-parent`（Spring Boot 4.1.0、JDK 21、Flowable 8.0.0 Process）。旧的 `管理端`/`员工端`/`其他` 前端不是目标产品。系统依赖（Maven、JDK 21、MySQL 8、Redis）已装入环境镜像；update 脚本只刷新 `npm install` 与 `mvn -DskipTests install`。

### 服务与端口

| 服务 | 位置 | 命令 | 端口 |
|---|---|---|---|
| 后端 service-oa | `server/guigu-oa-parent/service-oa` | 见下方启动说明 | 8800 |
| 前端统一客户端 | `client/guigu-oa-client` | `npm run dev` | 9530 |
| MySQL | 系统服务 | `sudo service mysql start` | 3306 |
| Redis | 系统服务 | `sudo service redis-server start` | 6379 |

前端 Vite 把 `/dev-api` 代理到 `http://localhost:8800`，所以后端必须在 8800 运行。客户端可见前缀仍是 `/dev-api`（`VITE_APP_BASE_API`）。

### 后端启动

1. **用 JDK 21 编译和运行**（`java.version` / `--release` 21）。
2. `service-oa` 的 `pom.xml` 已把 `src/main/resources/**` 打进 jar。在 `service-oa` 目录先 `mvn -DskipTests install`，再：
   `java -jar target/service-oa.jar`
   若本地 jar 仍读不到配置，可临时加 `--spring.config.additional-location=file:./src/main/resources/`。
3. Flowable 的 `ACT_*` 表由引擎按 `application-dev.yml` 的 `flowable.database-schema-update: true`、`flowable.history-level: full` 维护；**空库首启动当前在 MySQL 8.4 上失败**（误走 schema upgrade、0 张引擎表，#158），已有库不受影响。修复前本地起真实服务验证：连已有库 + `--spring.flyway.validate-on-migrate=false`，配方见 `docs/agents/testing.md` 服务端节。从 Activiti 7 切库见 `server/guigu-oa-parent/sql/flowable8-cutover.md`。
4. OpenAPI / Swagger UI：只在 `dev` profile 下无需登录可读（`/v3/api-docs`、`/swagger-ui.html`）。Compose 演示栈（`dev,demo`）与 `prod` 都关闭这两个端点（#140）。业务接口仍用请求头 `token`。
5. Compose 构建：本机 `docker compose build service-oa` 直连 Maven Central 会被代理打断 TLS 握手，需带镜像源 `MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose build service-oa`。

### 数据库初始化

后端连接 MySQL 库 `guigu-oa`（`root`/`root`）。库不存在时先创建空库：
```sh
mysql -uroot -proot -h127.0.0.1 -e 'CREATE DATABASE IF NOT EXISTS `guigu-oa` CHARACTER SET utf8mb4;'
```
首次启动 `service-oa` 时，Flyway 自动创建业务 `sys_*` / `oa_*` 表并写入基础种子，迁移位于 `server/guigu-oa-parent/service-oa/src/main/resources/db/migration/`；`service-oa/src/main/java/db/migration/` 下还有 Java 迁移（如 V104），选下一个版本号时两个目录一起看。这是空库启动路径；由旧初始化脚本创建的现有库不会自动 baseline，切换前需另行安排 baseline。`ACT_*` / `FLW_*` 引擎表仍由 Flowable 根据 `application-dev.yml` 自动创建（空库首启动的当前限制见「后端启动」第 3 条与 #158）。种子登录账号：**用户名 `admin` / 密码 `111111`**（初始密码使用 BCrypt；旧 MD5 密码会在首次成功登录后升级为 BCrypt；`admin` 通过种子角色「配置管理员」获得配置菜单权限，`userId=1` 不再特殊）。配置类接口由服务端 `configurationMenuGate` 按菜单授予统一判定；`user` 种子没有配置角色，只用于办事。生产环境需启用 `prod` profile，并通过 `JWT_SECRET` 注入至少 64 字节的 JWT 签名密钥。


### 前端检查与构建

- `npm run verify`：lint + typecheck + format:check + test:unit + 文档检查（模块地图路径、Markdown 相对链接、证据路径与体积）+ shell 检查（提交前必跑）。
- `npm run test:e2e`：Playwright；默认自动启动 9530 dev server，设置 `E2E_BASE_URL` 指向已构建客户端时不再启动（兼容性矩阵）。
- `npm run test:api`：接口套件（打已运行的 Compose 栈，见 `playwright.api.config.ts`）。**需要干净演示栈**：`globalSetup` 检测到压测造数数据（alice 已发起超过 2000 条）会直接报错，先 `docker compose down --volumes --remove-orphans && docker compose up -d --build --wait`。改了服务端必须带 `--build` 起栈：直接 `up -d` 会沿用旧 jar，新用例红得莫名其妙、旧套件假绿；`./scripts/test-round.sh` 的套件阶段会自己重建。
- `npm run build:prod`：类型检查 + 生产构建。

### 仓库检查

`./scripts/verify.sh`：客户端 `npm run verify` + 服务端 `mvn -pl service-oa -am test`。端到端与生产构建仍单独跑（见下）。

五类测试（接口/功能/性能/安全/兼容性）一键复跑：`./scripts/test-round.sh all`（按类子命令与前置见 `--help`；方案与证据在 `docs/test/`）。常态化拓扑见 ADR 0030：PR 维持轻量，push main 跑栈回归，每周跑安全，性能只在本地。

### 后端测试

`cd server/guigu-oa-parent && mvn -pl service-oa -am test`。合同测试使用 H2 与内存中的 Redis，不需要本地 MySQL/Redis。跑或改服务端测试前先读 `docs/agents/testing.md`（`-Dtest` 跨模块 flag、合同测试 schema、MyBatis 拦截器签名覆盖都在里面；改校验或失败路径的消息/顺序/码之前尤其要读负例约定）。当前只有 `service-oa` 有测试目录；若其他模块新增测试，改用父目录的 `mvn test`。改过 `src/test/resources` 后先 `mvn clean test`：Maven 不会清理 `target/test-classes` 里已删除的资源，旧配置会继续生效。单次运行会刷出数十 KB 结构化日志：重定向到文件后 `grep "Tests run:"` 看汇总；不要加 `-q`（会吞掉 surefire 汇总）。

### 测试覆盖率

覆盖率只报告、不卡红，不属于 `npm run verify` 或 `scripts/verify.sh` 的一部分。客户端跑 `npm run coverage`；服务端在父目录跑 `mvn test`，聚合报告落在 `coverage-report/target/site/jacoco-aggregate/`。`./scripts/coverage.sh` 一次跑两端并打印汇总。分母口径与排除项见 README「测试与覆盖率」；测试分层、共享 harness 与服务端单元测试坑见 `docs/agents/testing.md`。
