# 升级到 Spring Boot 4 与 Vue 3 的依赖规格

> **地图**：[升级到 Spring Boot 4 与 Vue 3 的依赖规格](https://github.com/lautung/guigu-oa/issues/22)  
> **领域词汇**：仓库根目录 [`CONTEXT.md`](../../CONTEXT.md)  
> **办事 / 配置合同面**：[云尚办公统一客户端改造规格](./云尚办公统一客户端改造规格.md)（本文不改写那份正文；栈与引擎改口只写在这里）  
> **坐标时点**：2026-08

本文档是可交出执行的升级规格：`service-oa` 送到 Spring Boot 4.1.0，统一客户端送到 Vue 3 + Vite 8。不在本文改代码。

---

## 1. 目标

- 后端 `server/guigu-oa-parent`（`service-oa`）一次跳到 **Spring Boot 4.1.0**，JDK **21**，审批引擎换成 **Flowable 8.0.0** Process starter
- 前端 `client/guigu-oa-client` 一次跳到 **Vue 3.5 + Vite 8**，组件库 **Element Plus**
- 办事必须能在手机浏览器完成；配置仍按桌面
- 合同面不动：审批模板流程 zip、待处理 / 已处理 / 已发起、HTTP 路径与请求响应形状、`oa_process` / `oa_process_record`

---

## 2. 不在本次规格内

| 项 | 说明 |
|---|---|
| 旧管理端、员工端、其他前端的依赖 | 不是目标产品 |
| MySQL、Redis 本身的版本 | 地图范围外 |
| 在执行本规格之前于本图改代码 | 本图只出规格 |
| 修 `findStarted` 的 MyBatis BindingException | 既有缺陷，不顺手修 |
| 为修而修 ESLint | 除非 Vue 3 构建被迫带上 |
| 重做审批产品语义 | 模板、待处理 / 已处理 / 已发起 不变 |
| 改写统一客户端改造规格正文 | 合同面仍以那份为准 |
| iframe 流程设计器 | 主路径仍是流程 zip |
| 把 Flowable BOM 引进 parent | 其 BOM 钉 Boot 4.0.2，不得覆盖 4.1.0 |
| Activiti 7 运行时迁进 Flowable 8 | 无官方路径，见 §6 |

---

## 3. 锁定坐标

### 3.1 后端（service-oa）

| 栈 | 现坐标 | 规格锁 | 理由 / 停在哪 |
|----|--------|--------|----------------|
| Spring Boot | `2.3.6.RELEASE` | `spring-boot-starter-parent` **4.1.0** | 2026-08 latest stable；OSS minor 至少到 2027-06-10。不落 4.0.7。一次跳到 4，不经 Boot 3 |
| JDK / 编译 | 8 / compiler 3.1 / `source/target 1.8` | `java.version` / `--release` **21**；compiler 落到 BOM **3.15.0** | 去掉父 POM 钉死的 compiler 3.1。不再为 jjwt 0.9.1 绑 JDK8 |
| HTTP JSON | Fastjson 2.0.21 + Jackson 2 | **Jackson 3**（随 4.1.0 BOM，3.1.4；不另钉） | 坐标表不再列 Fastjson。不要加 `spring-boot-jackson2` |
| API 文档 | `knife4j-spring-boot-starter` 3.0.3 / Springfox | `org.springdoc:springdoc-openapi-starter-webmvc-ui` **3.1.0** | xiaoymin 无 Boot 4 starter。不用无 UI 的 `…-webmvc-api` |
| MySQL 驱动 | `mysql:mysql-connector-java:8.0.30` | `com.mysql:mysql-connector-j`，版本跟 BOM **9.7.0** | 父 POM 不再钉 `mysql.version`。不追 Oracle 26.7.0 |
| JWT | 单体 `jjwt:0.9.1` | `jjwt-api` + `jjwt-impl` + `jjwt-jackson` 皆 **0.13.0** | JWT 不接到 Jackson 3（`jjwt-jackson` 可继续传递 Jackson 2） |
| MyBatis-Plus | `mybatis-plus-boot-starter` 3.4.1 | `mybatis-plus-spring-boot4-starter` **3.5.17**；generator 一并 **3.5.17** | 规格不展开 generator API |
| Spring Security | 随 Boot 2.3 | `spring-boot-starter-security` **不钉版本** → **7.1.0** | 随 4.1.0 BOM |
| 审批引擎 | `activiti-spring-boot-starter:7.1.0.M6` + `activiti-modeler` / `activiti-image-generator` 5.22.0 | `org.flowable:flowable-spring-boot-starter-process` **8.0.0** | 只带 Process 引擎。不 import `flowable-dependencies`。不另加 image-generator |

配置前缀：`spring.activiti.*` → `flowable.*`。Java 包：`org.activiti.engine.*` → `org.flowable.engine.*` 同名服务（`RepositoryService` / `RuntimeService` / `TaskService` / `HistoryService`）。

若 Flowable 自带 MyBatis 与 MyBatis-Plus 冲突，沿用现 Activiti starter 的 `mybatis` exclusion。

### 3.2 引擎配置对照

| 现 `spring.activiti.*` | 规格 `flowable.*` |
|------------------------|-------------------|
| `database-schema-update: true` | `flowable.database-schema-update: true` |
| `db-history-used: true` | `flowable.db-history-used: true` |
| `history-level: full` | `flowable.history-level: full`（Boot 默认是 `audit`，必须显式 `full`，已处理才与现网一致） |
| `check-process-definitions: true` | 见 §6：不要当成模板恢复手段 |

自动部署目录默认值不同（Activiti `classpath*:/**/processes/` vs Flowable `classpath*:/processes/`）。产品主路径是 zip 上传，不靠 classpath 样例。

### 3.3 统一客户端

| 栈 | 现坐标 | 规格锁 | 理由 / 停在哪 |
|----|--------|--------|----------------|
| Node | （未锁） | `engines.node` **`^22.18.0`** | Vite 8 要求 20.19+ 或 22.12+；create-vue 为 22.18+ 或 24.12+。Node 20 已 EOL。不锁 Node 24 |
| 构建 | vue-cli 4.4.4 | `vite` **8.2.1**；`@vitejs/plugin-vue` **6.0.8** | 一次跳当前 major，不经 6/7 |
| Vue | 2.6.10 | `vue` **3.5.41**；SFC 走 `vue/compiler-sfc` | 不用 `@vue/compat`，不另装 `vue-template-compiler` / 独立 `@vue/compiler-sfc` |
| 路由 | vue-router 3.0.6 | `vue-router` **5.2.0** | 手写 `routes`，不用文件路由。不锁 4.6.4 |
| 状态 | vuex 3.1.0 | `pinia` **4.0.3** | 现 store 只有 `user` 一块。不双库并存。字段与动作名不变（token / name / avatar / menus；login、logout、getInfo、resetToken） |
| 组件库 | element-ui 2.13.2 | `element-plus` **2.14.4** | 设计器 peer `^2.11.1` 之上。办事与配置同一套 Plus。已否决 vant |
| 表单渲染 | `@form-create/element-ui` 2.5.17 | `@form-create/element-ui` **3.3.3** | 包名仍叫 `element-ui`，内容已是 Plus。不跟 `latest` 2.x |
| 表单设计器 | `@form-create/designer` 1.0.8 | `@form-create/designer` **3.5.0** | 组件仍是 `<fc-designer>`；`setRule` / `getRule` / `setOption` / `getOption` 仍是写出/回填面 |
| HTTP | axios 0.18.1 | `axios` **1.19.0** | 拦截器须 `return Promise.reject` / throw。不锁 0.33.0 |
| 测试 | Jest + VTU 1 | `vitest` **4.1.11**；`@vue/test-utils` **2.4.11** | 脚本名仍 `test:unit`，实现 `vitest run` |
| Sass | sass 1.26.8 + sass-loader | `sass` **1.102.0**；**不装** `sass-loader` | Plus ≥2.8.5 要 ≥1.79.0 |
| cookie | js-cookie 2.2.0 | `js-cookie` **3.0.8** | `get/set/remove` 仍是该库表面 |
| 进度条 | nprogress 0.2.0 | `nprogress` **0.2.0** | 不跟预发布 `1.0.0-1` |
| 重置样式 | normalize.css 7.0.0 | `normalize.css` **7.0.0** | 无强迫升级 |

删除、不写后继：`@vue/cli-*`、Babel / `core-js`、Jest / `vue-jest` / `babel-jest`、`element-ui`、`vuex`、`sass-loader`、`vue-template-compiler`、`activiti-modeler`、`activiti-image-generator`。ESLint 仍不进规格。钉死补丁号，不写会漂的 `@latest` / `@next` dist-tag。

#### Vite 替换 vue-cli（合同面）

| 现 vue-cli | 规格锁的后继 |
|---|---|
| `dev` / `build:prod` / `test:unit` | `vite` / `vite build` / `vitest run`（脚本名不变） |
| 端口 9530 | `server.port` **9530** |
| `devServer.proxy['/dev-api']` → `localhost:8800`，剥 `/dev-api` | Vite `server.proxy` 同等行为；客户端可见前缀仍是 `/dev-api` |
| `VUE_APP_BASE_API`（`process.env`） | `import.meta.env.VITE_APP_BASE_API`，默认仍 `/dev-api`。**不要**用 `envPrefix: 'VUE_'` 假装 vue-cli |
| `@` → `src` | `resolve.alias` 同等 |

不写：webpack loader 对照、`lintOnSave`、source map 开关、整文件抄本。

#### 浏览器 / 手机办事

不要 `@vitejs/plugin-legacy`，不要 `core-js`。`build.target`（或等价 browserslist）对齐 Element Plus ≥2.5 文档矩阵：

- Chrome / Edge ≥ 85
- Firefox ≥ 79
- Safari ≥ 14.1（约 iOS 14.5）
- **不支持 IE11**

构建目标不得静默严于 Plus 矩阵。Vite 下 form-create 走其 `auto-import` 辅助，不要按 Element Plus 的 unplugin 示例去接设计器。

---

## 4. 后端逐步迁移剧本

仍不在本图改代码。执行时按此顺序，每一步点名必须动的面；不写 class-by-class diff。

1. **BOM 与编译**  
   parent 改为 4.1.0；`java.version` / `release` = 21；删除 compiler 3.1 覆盖。此步之后项目按 JDK 21 编译，尚未要求能启动。

2. **换 GAV**  
   按 §3.1 替换 MP / connector-j / jjwt 三件套 / springdoc / Flowable Process starter；删除 Knife4j、旧 `jjwt`、旧 `mysql-connector-java`、`com.alibaba:fastjson` 的 dependencyManagement 与模块依赖；删除 `activiti-spring-boot-starter`、`activiti-modeler`、`activiti-image-generator`。Security 不写版本。不 import Flowable BOM。

3. **`javax.servlet` → `jakarta.servlet`**  
   过滤器、`ResponseUtil`、登录与审批导出等所有 Servlet API 引用。这是 Boot 4 / Jakarta EE 11 硬门槛。

4. **HTTP JSON → Jackson 3**  
   `com.fasterxml.jackson.databind.ObjectMapper` 等改为 Jackson 3（`tools.jackson.*`），按 [Spring Boot 4.0 Migration Guide — Upgrading Jackson](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) 改构造与配置。核对 `application-dev.yml` 的 `spring.jackson.date-format` / `time-zone`。**不要**加 `spring-boot-jackson2`。把 `TokenLoginFilter`、`TokenAuthenticationFilter`、`OaProcessServiceImpl` 里的 Fastjson / fastjson2 调用改到 Jackson 3。

5. **`JwtHelper` → jjwt 0.13 API**  
   废弃 `Jwts.parser().setSigningKey(String)` / `signWith(SignatureAlgorithm, String)`。0.13 要求足够长度的 HMAC key；现密钥 `"123456"` 不能再当 HS512 签名键。换成满足 HS512 长度的密钥材料；具体密钥值不是本规格的产品决策。

6. **Spring Security 7**  
   删除 `WebSecurityConfigurerAdapter`；改为 `SecurityFilterChain`（及 Security 7 的 `EnableMethodSecurity` 等后继）。白名单从 Springfox `/swagger-resources/**`、`/v2/**`、`/swagger-ui.html/**`、`/doc.html` 换成 springdoc 的 `/v3/api-docs/**`、`/swagger-ui/**`（含 `/swagger-ui.html`）。登录过滤器链与「待处理列表就是通知」的产品语义不变。

7. **API 文档**  
   删除 `Knife4jConfig`（Springfox `Docket`）。依赖 springdoc starter 默认暴露 `/v3/api-docs` 与 Swagger UI。`io.swagger.annotations` 改为 `io.swagger.v3.oas.annotations`（一句话带过，不枚举每个 `ApiModel` 文件）。token 请求头用 springdoc 配置面，不再用 Docket `ParameterBuilder`。

8. **引擎 API 与配置**  
   包名换到 `org.flowable.engine.*`；`spring.activiti.*` 换成 §3.2 的 `flowable.*`。拒绝仍走「改当前节点流向到结束再 complete」的 Flowable 等价 API。流程 zip 的 `activiti:` 命名空间在再部署时仍解析，不要求用户改成 `flowable:` 才能发布。

9. **启动**  
   用 JDK 21 跑。`service-oa` 自定义 `<resources>` 导致 yml 不进 jar 的既有约束仍在：Boot 4 启动必须能加载 datasource / 端口 8800（继续用 `--spring.config.additional-location=file:./src/main/resources/`，或改资源打包使 yml 进 jar，二选一）。不修 `findStarted` 的 BindingException。

引擎表与进行中审批的切库步骤见 §6，插在首次用 Flowable 连上现网库之前。

---

## 5. 前端逐步迁移剧本

1. **Vite 底座**  
   用 Vite 8.2.1 + plugin-vue 6.0.8 替换 vue-cli；`engines.node` = `^22.18.0`；按合同面接代理、端口、`VITE_APP_BASE_API`、`@` alias。此步之后能起开发服务打到 8800，页面尚未迁 Vue 3 API。

2. **换 npm 坐标**  
   按 §3.3 替换 vue / vue-router / pinia / element-plus / axios / sass / js-cookie；装上 form-create 两包；删除 §3.3「删除」列。

3. **应用实例与路由**  
   `new Vue` / `Vue.use` → `createApp` + `app.use` / `app.mount`。`new Router` → `createRouter` + `createWebHistory`（或现用的 history 模式对等物）。模板里 Vue 2 语法（配置页 `slot-scope` 等）按 Vue 3 改。Options API 可留。

4. **Pinia**  
   `user` 收成一个 Pinia store；删 namespaced module / mutation；`mapGetters` 换成 Pinia 映射。不保留 Vuex。

5. **Element Plus**  
   `Vue.use(ElementUI, { size: 'small' })` → `app.use(ElementPlus, { size: 'small' })`。`Message` 等改为 Plus 的 `ElMessage` API。按 Plus 迁移说明改组件差异；规格不枚举每个 `el-*`。

6. **form-create 3.x 与 JSON 回放**  
   设计器仍 `<fc-designer>`，办事仍 `<form-create>`。落库形状不变：`formProps` = `JSON.stringify(rule)`，`formOptions` = `JSON.stringify(option)`。第一方**没有**保证 2.5.x JSON 在 3.x 上零修改可渲染——用现网或种子模板的 `formProps` / `formOptions` 走通三条路径：配置向导打开设计器并回填、配置列表预览、办事发起渲染并提交。坏了再改 JSON 或补迁移，而不是假设兼容。Vite 下走 form-create 的 `auto-import` 辅助。

7. **axios 1**  
   拦截器错误路径改为显式 reject；`baseURL` 改读 `import.meta.env.VITE_APP_BASE_API`。

8. **Vitest + VTU 2**  
   用例语义不变；`createLocalVue` 删除、`mocks` / `stubs` 进 `global`、`destroy`→`unmount`。`test:unit` = `vitest run`。

9. **构建目标**  
   `build.target` 钉在 Plus 矩阵；确认办事手机路径在 Safari ≥14.1 量级可完成。

---

## 6. 既有引擎表与进行中审批

官方没有 Activiti 7.1.0.M6 → Flowable 8 的升级路径。本库 `ACT_GE_PROPERTY.schema.version` 是 `7.1.0-M6`，Flowable 8.0.0 不认该字符串；`databaseSchemaUpdate=true` 会 **unknown version**，不会当 Flowable 7.1 自动升。同名 `ACT_*` 列已经分叉。`flowable5CompatibilityEnabled` 只覆盖 v5，罩不到 Activiti 7。

规格因此 **不迁运行时**：丢掉既有引擎表，空库再建。流程 zip 再部署时 `xmlns:activiti` / `activiti:assignee` / `activiti:candidateUsers` 仍解析。`oa_process.processInstanceId` 格式无断裂，但旧 id 能否对上取决于引擎能否读旧表——自动升级做不到，故旧实例 id 作废。

### 6.1 硬前提

切库前 **`oa_process.status=1`（审批中）必须为 0 条**。先在产品里办完或拒绝。否则升级停。

不把审批中行脚本改成驳回（`-1`），不删已完成账本，不把审批中行原样留下（否则已发起仍显示能办、待处理为空）。

### 6.2 切库步骤

插在首次用 Flowable 8 连接现网 `guigu-oa` 库之前。

1. 确认 §6.1。
2. 可选：备份整个库或仅 `ACT_%` 表。**备份不得再被 Flowable 连接。**
3. **`DROP` 全部 `ACT_%` 表**（含 Activiti 独有表，如 `ACT_RU_INTEGRATION`）。**禁止**改 `schema.version` 骗升级。
4. 不要动 `oa_*` / `sys_*`。已完成的 `oa_process` / `oa_process_record` 原样留。
5. 以 Flowable 8 启动：`flowable.database-schema-update=true` 在空库上建 `ACT_*`，并会带上 `FLW_RU_BATCH*`。`flowable.history-level=full`。
6. 对每条 **已发布** 审批模板，按现存流程 zip（`process_definition_path`）再 `deploy` 一次——与现有「发布」的 deploy 同路。**不改模板状态，不改 HTTP，不让人去点发布。** zip 内流程定义 key 不变，`startProcessInstanceByKey` 仍用该 key。
7. **不要**把 `check-process-definitions` / classpath 样例 BPMN 当成已发布模板的恢复路径。执行时关掉 classpath 自动部署（`flowable.check-process-definitions=false`），以免样例定义混进空库。

### 6.3 切库后用户能看见什么

| 面 | 切库后 |
|----|--------|
| 待处理 | 空，直到有新的待办（引擎运行时是空的） |
| 已处理 | **只反映切库后的引擎历史**。不改查询面（仍 `HistoricTaskInstanceQuery.finished()`），不扫账本去补切库前已办完的 |
| 已发起 | 仍扫 `oa_process`；切库前已办完的行还在 |
| 审批详情 | 走 `oa_process` + `oa_process_record`。旧 `processInstanceId` 不再对上引擎；问当前任务得空列表，`isApprove=false`，不挡只读详情 |
| 配置里的审批实例查询 | 仍扫 `oa_process` |
| 发起 | 模板仍是已发布；zip 已再 deploy 后可发起新审批 |

---

## 7. 合同面（不动）

用户可见的办事 / 配置保持：

- **审批模板**：三步向导 + **流程 zip** 上传 + 发布。zip 仍是 BPMN。对用户不说 Activiti / Flowable
- **待处理**：当前用户待办（`TaskQuery.taskAssignee`）
- **已处理**：当前用户已完成任务（`HistoricTaskInstanceQuery.finished`）
- **已发起**：扫 `oa_process`，不查引擎
- **通过 / 拒绝**：仍 `complete`；拒绝仍改当前节点流向到结束再 complete
- HTTP 路径与请求/响应形状、`oa_process` / `oa_process_record` 形状：**不动**
- 表单落库：`formProps` / `formOptions` 仍是 form-create 的 rule / option JSON；配置桌面画表，办事只渲染已发布 JSON

上一张地图「沿用 Activiti」在本图复议作废；产品规格正文不改写，改口以本文为准。

---

## 8. 决策索引

| 票 | gist |
|---|---|
| [Activiti 7.1.0.M6 能否跟上 Spring Boot 4](https://github.com/lautung/guigu-oa/issues/23) | 无官方 GA 的 Boot 4 路径；Flowable 8.0.0 提供 Boot 4 starter |
| [Spring Boot 4 线与后端生态坐标](https://github.com/lautung/guigu-oa/issues/24) | 4.1.0 / Jakarta 11 / Jackson 3；现坐标均须换 |
| [Vue 3 + Vite 下统一客户端依赖的可用后继](https://github.com/lautung/guigu-oa/issues/25) | form-create Vue 3 + Plus 后继；其余 vue 3 / router / Pinia / Plus / Vite |
| [规格采用哪条 Spring Boot 4 线及后端坐标](https://github.com/lautung/guigu-oa/issues/26) | 锁 4.1.0 与后端表；断裂写成逐步剧本 |
| [审批表单设计器换什么实现](https://github.com/lautung/guigu-oa/issues/27) | form-create 3.3.3 + designer 3.5.0；JSON 回放门禁 |
| [统一客户端其余 npm 与构建栈坐标](https://github.com/lautung/guigu-oa/issues/28) | Node 22.18、Vite 8、Vue 3.5、router 5、Pinia、Plus 2.14.4、Vitest |
| [审批引擎跟不上 Boot 4 时规格怎么写](https://github.com/lautung/guigu-oa/issues/29) | 守住 Boot 4.1.0，换 Flowable 8.0.0；合同面不动 |
| [Flowable 8 如何承接 Activiti 7 存量](https://github.com/lautung/guigu-oa/issues/30) | 仍用 `ACT_*`；无 7→8 自动升级；zip 的 `activiti:` 再部署仍解析 |
| [规格对既有引擎表与进行中审批怎么写](https://github.com/lautung/guigu-oa/issues/31) | 不迁运行时；硬前提；DROP `ACT_*`；zip 再 deploy；已处理不对称 |
| [升级规格正文放哪](https://github.com/lautung/guigu-oa/issues/32) | 本文件；地图当索引；不另开 ready-for-agent |
