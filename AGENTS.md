## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues (via the `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical roles map 1:1 to tracker labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Cursor Cloud specific instructions

目标产品是**统一客户端** `前端代码/guigu-oa-client`（Vue 3.5 + Vite 8 + Element Plus + Pinia）配合后端 `后端代码/guigu-oa-parent`（Spring Boot 4.1.0、JDK 21、Flowable 8.0.0 Process）。旧的 `管理端`/`员工端`/`其他` 前端不是目标产品。系统依赖（Maven、JDK 21、MySQL 8、Redis）已装入环境镜像；update 脚本只刷新 `npm install` 与 `mvn -DskipTests install`。

### 服务与端口

| 服务 | 位置 | 命令 | 端口 |
|---|---|---|---|
| 后端 service-oa | `后端代码/guigu-oa-parent/service-oa` | 见下方启动说明 | 8800 |
| 前端统一客户端 | `前端代码/guigu-oa-client` | `npm run dev` | 9530 |
| MySQL | 系统服务 | `sudo service mysql start` | 3306 |
| Redis | 系统服务 | `sudo service redis-server start` | 6379 |

前端 Vite 把 `/dev-api` 代理到 `http://localhost:8800`，所以后端必须在 8800 运行。客户端可见前缀仍是 `/dev-api`（`VITE_APP_BASE_API`）。

### 后端启动

1. **用 JDK 21 编译和运行**（`java.version` / `--release` 21）。不要再用 JDK 8 启动路径。
2. `service-oa` 的 `pom.xml` 已把 `src/main/resources/**` 打进 jar。在 `service-oa` 目录先 `mvn -DskipTests install`，再：
   `java -jar target/service-oa.jar`
   若本地 jar 仍读不到配置，可临时加 `--spring.config.additional-location=file:./src/main/resources/`。
3. Flowable 的 `ACT_*` 表由引擎在空库上首启动时自动建（`application-dev.yml`: `flowable.database-schema-update: true`，`flowable.history-level: full`）。从 Activiti 7 切库见 `后端代码/guigu-oa-parent/sql/flowable8-cutover.md`。
4. OpenAPI / Swagger UI：`/v3/api-docs` 与 `/swagger-ui.html` 无需登录。业务接口仍用请求头 `token`。

### 数据库初始化

后端连接 MySQL 库 `guigu-oa`（`root`/`root`）。仓库**不含**业务表建表脚本，已补充 `后端代码/guigu-oa-parent/sql/guigu-oa-init.sql`（业务 `sys_*`/`oa_*` 表 + 种子超级管理员）。首次或重置库时执行：
`mysql -uroot -proot -h127.0.0.1 guigu-oa < 后端代码/guigu-oa-parent/sql/guigu-oa-init.sql`
（库不存在先 `CREATE DATABASE \`guigu-oa\` CHARACTER SET utf8mb4;`）。种子登录账号：**用户名 `admin` / 密码 `111111`**（密码存 MD5，无盐；`userId=1` 自动拥有全部配置菜单权限）。

### 前端 lint / test / build

- 单元测试：`npm run test:unit`（Vitest，脚本名不变）。
- 构建：`npm run build:prod`；开发：`npm run dev`。
- Node：`engines.node` `^22.18.0`。
- `npm run lint` 目前**无法运行**：仓库未提交 ESLint 配置文件。这是仓库既有状态，验证以单元测试为准。

### 已知应用缺陷（非环境问题）

`已发起` 列表接口 `/admin/process/findStarted` 的既有 MyBatis 绑定问题不在本次升级范围（升级规格明确不顺手修）。`findPending`/`findProcessed` 正常。登录、`待处理`、`已处理` 均可用。
