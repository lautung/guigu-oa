## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues (via the `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical roles map 1:1 to tracker labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Cursor Cloud specific instructions

目标产品是**统一客户端** `前端代码/guigu-oa-client`（Vue 2 + vue-cli）配合后端 `后端代码/guigu-oa-parent`（Spring Boot 2.3.6 多模块）。旧的 `管理端`/`员工端`/`其他` 前端不是目标产品。系统依赖（Maven、JDK8、MySQL8、Redis）已装入环境镜像；update 脚本只刷新 `npm install` 与 `mvn -DskipTests install`。

### 服务与端口

| 服务 | 位置 | 命令 | 端口 |
|---|---|---|---|
| 后端 service-oa | `后端代码/guigu-oa-parent/service-oa` | 见下方启动说明 | 8800 |
| 前端统一客户端 | `前端代码/guigu-oa-client` | `npm run dev` | 9530 |
| MySQL | 系统服务 | `sudo service mysql start` | 3306 |
| Redis | 系统服务 | `sudo service redis-server start` | 6379 |

前端 `vue.config.js` 把 `/dev-api` 代理到 `http://localhost:8800`，所以后端必须在 8800 运行。

### 后端启动（关键 gotcha）

1. **必须用 JDK8 运行**：jjwt 0.9.1 依赖 `javax.xml.bind.DatatypeConverter`，该类在 JDK11+ 被移除。环境默认 `java`/`mvn` 是 JDK21，会导致登录时 `ClassNotFoundException`。用 JDK8 二进制启动：`/usr/lib/jvm/java-8-openjdk-amd64/bin/java`（构建也需 `JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64`）。
2. **必须显式加载配置**：`service-oa` 的 `pom.xml` 自定义了 `<resources>`，导致 `application.yml`/`application-dev.yml` 不会被打进 jar（`target/classes` 里没有 yml）。用 `java -jar` 运行时要加 `--spring.config.additional-location=file:./src/main/resources/`，否则 datasource 未配置、端口回退 8080 启动失败。
3. 完整启动命令（在 `service-oa` 目录，先 `mvn -DskipTests install` 生成 `target/service-oa.jar`）：
   `/usr/lib/jvm/java-8-openjdk-amd64/bin/java -jar target/service-oa.jar --spring.config.additional-location=file:./src/main/resources/`
4. Activiti 的 `ACT_*` 表由引擎首启动时自动建（`application-dev.yml: database-schema-update: true`）。

### 数据库初始化

后端连接 MySQL 库 `guigu-oa`（`root`/`root`）。仓库**不含**业务表建表脚本，已补充 `后端代码/guigu-oa-parent/sql/guigu-oa-init.sql`（业务 `sys_*`/`oa_*` 表 + 种子超级管理员）。首次或重置库时执行：
`mysql -uroot -proot -h127.0.0.1 guigu-oa < 后端代码/guigu-oa-parent/sql/guigu-oa-init.sql`
（库不存在先 `CREATE DATABASE \`guigu-oa\` CHARACTER SET utf8mb4;`）。种子登录账号：**用户名 `admin` / 密码 `111111`**（密码存 MD5，无盐；`userId=1` 自动拥有全部配置菜单权限）。

### 前端 lint / test / build

- 单元测试：`npm run test:unit`（Jest，已验证 40 用例通过）。
- 构建：`npm run build:prod`；开发：`npm run dev`。
- `npm run lint` 目前**无法运行**：仓库未提交 ESLint 配置文件，且 `vue.config.js` 里 `lintOnSave:false`。这是仓库既有状态，验证以单元测试为准。

### 已知应用缺陷（非环境问题）

`已发起` 列表接口 `/admin/process/findStarted` 会抛 `MyBatis BindingException: Parameter 'ew' not found`（`OaProcessServiceImpl.findStarted` 与 mapper 参数不匹配的既有代码 bug）。`findPending`/`findProcessed` 正常。登录、`待处理`、`已处理` 均可用。
