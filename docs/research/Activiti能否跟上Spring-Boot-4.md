# Activiti 7.1.0.M6 能否跟上 Spring Boot 4

> 研究票：[Activiti 7.1.0.M6 能否跟上 Spring Boot 4](https://github.com/lautung/guigu-oa/issues/23)  
> 地图票：[升级到 Spring Boot 4 与 Vue 3 的依赖规格](https://github.com/lautung/guigu-oa/issues/22)  
> 来源：本仓库 `后端代码/guigu-oa-parent`（pom、zip 部署、待处理/已处理/已发起、通过/拒绝）；Activiti / Flowable / Spring 官方文档与 GitHub 发布；Maven Central 与 Alfresco Nexus 坐标。  
> 说明：在线 iframe 设计器（`onlineProcessSet`）不在必须跟上的范围；本页只报事实与来源，不推荐产品选型。

---

## 结论

**没有。** 当前栈 `org.activiti:activiti-spring-boot-starter:7.1.0.M6` 以及 `activiti-modeler` / `activiti-image-generator` 5.22.0，在 Spring Boot 4 / Spring Framework 7 / Jakarta EE 11 / JDK 21 下**没有官方文档化的、已发布 GA 的可维护升级路径**。

依据：

- `7.1.0.M6` 是 2020 年的 **Milestone**，官方绑定 **Spring Boot 2.1.12.RELEASE**、**Java 8**，构件仍在 Maven Central；其后同一坐标在 Central 上没有更新。
- Activiti 官方把 **Java 17 + Spring Boot 3** 当作 8.0.0 的技术跃迁；最新 **GA 8.8.1** 的根 POM 仍是 **Spring Boot 3.5.7** / **Java 21** / `jakarta.*`，**不是 Boot 4**。8.x 起官方要求从 **Alfresco Nexus** 取构件，Central 上没有 `8.8.1` starter。
- Boot 4 只出现在 **未 GA** 的线上：`develop` 与 `9.1.0-alpha.61` 根 POM 为 **Spring Boot 4.0.7** / **Java 25** / `jakarta.el-api` 6.0.1；GitHub 上 `7.21.0-rc.*` 也标为 prerelease。没有 Boot 4 迁移指南。
- `activiti-modeler` 停在 Activiti **5.x**（Central 最新 5.23.0，Java 1.6）；仓库里的 5.22.0 **没有** 7/8/9 同名后继。iframe 设计器已排除，该坐标不必跟上 Boot 4。

社区同源引擎 **Flowable**（官方写明是 Activiti 的 fork）在 Maven Central 上有 **GA `8.0.0`**：`flowable-spring-boot-starter` / `flowable-spring-boot-starter-process`，BOM 钉 **Spring Boot 4.0.2**、**Spring Framework 7.0.3**、**Jakarta Servlet 6.1.0**（EE 11 基线），编译目标 **JDK 17**。Flowable 8 **不再支持 Spring Boot 3**。这不是选型建议，只是坐标与版本事实。

---

## 1. 本仓库实际用法（对照范围）

仓库后端父 POM 是 Spring Boot **2.3.6.RELEASE**、Java **1.8**（`后端代码/guigu-oa-parent/pom.xml`）。`service-oa` 显式依赖：

| 坐标 | 版本 | 文件 |
|------|------|------|
| `org.activiti:activiti-spring-boot-starter` | `7.1.0.M6` | `后端代码/guigu-oa-parent/service-oa/pom.xml` |
| `org.activiti:activiti-modeler` | `5.22.0` | 同上 |
| `org.activiti:activiti-image-generator` | `5.22.0` | 同上 |

配置：`spring.activiti.database-schema-update: true`、`db-history-used: true`、`history-level: full`、`check-process-definitions: true`（`application-dev.yml`）。引擎表 `ACT_*` 由首次启动自动建。

办事路径用的是 **Activiti 6/7 风格的 `org.activiti.engine.*` 服务**，不是 Getting Started 里推荐的 `ProcessRuntime` / `TaskRuntime` 新 API（官方仍未废弃旧 API，但建议新 API 以获得长期支持：<https://activiti.gitbook.io/activiti-7-developers-guide/getting-started/getting-started-activiti-core.md>）。

| 产品能力 | 实现 | 引擎 API |
|----------|------|----------|
| 审批模板 zip 部署 | `OaProcessTemplateServiceImpl.publish` → `OaProcessServiceImpl.deployByZip` | `RepositoryService.createDeployment().addZipInputStream(...).deploy()` |
| 发起 | `startUp` | `RuntimeService.startProcessInstanceByKey(definitionKey, businessKey, variables)`；业务行写入 `oa_process` |
| 待处理 | `ProcessController` `GET /admin/process/findPending/{page}/{limit}` → `findfindPending` | `TaskService.createTaskQuery().taskAssignee(username)` |
| 已处理 | `GET .../findProcessed/...` | `HistoryService.createHistoricTaskInstanceQuery().taskAssignee(...).finished()` |
| 已发起 | `GET .../findStarted/...` | **不查引擎**；按当前用户扫 `oa_process`（`findStarted`） |
| 通过 | `POST .../approve`，`status == 1` | `TaskService.complete(taskId, variables)` |
| 拒绝 | 同上，`status != 1` → `endTask` | 改 BPMN 当前节点 outgoing 到 `EndEvent`，再 `complete` |

`activiti-modeler`：`OaProcessServiceImpl` 仅有未使用的 `BpmnJsonConverter` import。iframe 相关静态路径在 `WebSecurityConfig` 放行（`/admin/modeler/**`、`/editor-app/**`、`/admin/processImage/**`）。统一客户端规格已排除在线设计器，主路径是 zip 上传（`docs/spec/云尚办公统一客户端改造规格.md`）。

`activiti-image-generator`：pom 声明了，业务 Java **没有** `ProcessDiagramGenerator` 等引用。

业务代码仍是 `javax.servlet.http.HttpServletResponse`（`OaProcessService` / `OaProcessServiceImpl`），与 Boot 2 / Java EE 8 命名空间一致。

---

## 2. Spring Boot 4 目标栈基线（官方）

| 项 | 事实 | 来源 |
|----|------|------|
| Spring Boot 4.0.7 | 至少 **Java 17**，兼容至 Java 26；需要 **Spring Framework 7.0.8+** | <https://docs.spring.io/spring-boot/4.0/system-requirements.html> |
| 嵌入式 Servlet | Tomcat 11 / Jetty 12.1 → **Servlet 6.1**；也可部署到任意 Servlet 6.1+ 容器 | 同上 |
| Spring Framework 7.x | JDK 17–25+；**Jakarta EE 11–12**（`jakarta` 命名空间）；EE 11 基线为 Servlet 6.1、JPA 3.2、BV 3.1 | <https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions> |
| Spring Framework 6.2（对照） | Jakarta EE 9–10 | 同上 |
| Spring Framework 5.3（对照，Boot 2 一代） | Java EE 7–8（`javax`） | 同上 |

因此：要把 service-oa 一次跳到 Boot 4，引擎必须提供 **Boot 4 starter（或等价自动配置）**、**`jakarta.*`（Servlet 6.1 级）**、并能在 **JDK 21** 上运行。`javax.servlet` / Boot 2 的 `7.1.0.M6` 对不上这条基线。

---

## 3. Activiti 7.1.0.M6 与 5.22.0：本身跟不上

### 3.1 `activiti-spring-boot-starter` 7.1.0.M6

- 官方发布页写明本里程碑升级到 **Spring Boot `2.1.12.RELEASE`** 与 Spring Cloud Greenwich.SR5，构件从 **Maven Central** 消费：<https://activiti.gitbook.io/activiti-7-developers-guide/releases/7.1.0.m6.md>
- 源码根 POM tag `7.1.0.M6`：`java.release` **8**，`spring-boot.version` **2.1.12.RELEASE**，animal-sniffer 忽略 `javax.servlet.*`：<https://github.com/Activiti/Activiti/blob/7.1.0.M6/pom.xml>
- Maven Central 该 artifact **latestVersion = 7.1.0.M6**，版本列表止于 7.0.0.Beta1 … 7.1.0.M6，**没有 8.x / 9.x**：<https://central.sonatype.com/artifact/org.activiti/activiti-spring-boot-starter>；`search.maven.org` `core=gav` 查询同结果。
- Getting Started 示例仍按 **Spring Boot 2** 写 starter，BOM 示例版本为 `7.1.0-M16`：<https://activiti.gitbook.io/activiti-7-developers-guide/getting-started/getting-started-activiti-core.md>

没有从 `7.1.0.M6` 升到 Boot 4 的官方迁移指南。gitbook 在 **8.2.0 之后不再生成发布页**，改为指向 GitHub Releases，并规定 8.x 构件从 Alfresco Nexus 取：<https://activiti.gitbook.io/activiti-7-developers-guide/releases.md>

### 3.2 `activiti-modeler` 5.22.0

- POM parent 是 `activiti-root` **5.22.0**，compiler **source/target 1.6**：<https://repo1.maven.org/maven2/org/activiti/activiti-modeler/5.22.0/activiti-modeler-5.22.0.pom>
- Central **latestVersion = 5.23.0**（无 7.x）：<https://central.sonatype.com/artifact/org.activiti/activiti-modeler>
- Activiti 8.8.1 根模块只有 `activiti-api` / `activiti-core` 等，**没有** `activiti-modeler` 模块：<https://github.com/Activiti/Activiti/blob/8.8.1/pom.xml>
- Central 上 `org.activiti:activiti-modeler:8.8.1` 为 **404**。

该坐标没有 Jakarta / Boot 4 后继。iframe 设计器已排除，不构成必须跟上的缺口；zip 部署不依赖它。

### 3.3 `activiti-image-generator` 5.22.0

- 仓库钉的是 **5.22.0**；Central 同 artifact 的 **latestVersion = 7.1.0.M6**（与 starter 同里程碑）：<https://central.sonatype.com/artifact/org.activiti/activiti-image-generator>
- 8.8.1 源码树 **仍有** `activiti-core/activiti-image-generator` 模块：<https://github.com/Activiti/Activiti/blob/8.8.1/activiti-core/pom.xml>
- Central 上 `…/activiti-image-generator/8.8.1/…` 为 **404**（8.x 不进 Central）。
- 本仓库业务代码未调用该库。即便要跟引擎版本走，5.22.0 本身也没有 Boot 4 坐标。

---

## 4. Activiti 8 / 9：官方后续停在 Boot 3 GA，Boot 4 未 GA

### 4.1 已发布 GA：8.0.0 → 8.8.1 = Spring Boot 3

- **8.0.0**（2023-09-25）官方说明：为 **Java 17 与 Spring Boot 3** 发布；Breaking Changes 含「Update Activiti to Spring Boot 3」；构件从 Alfresco Nexus：<https://activiti.gitbook.io/activiti-7-developers-guide/releases/8.0.0.md>、<https://github.com/Activiti/Activiti/releases/tag/8.0.0>
- **8.8.1**（2026-07-14，GitHub `prerelease: false`）根 POM：`java.version` **21**，`spring-boot.version` **3.5.7**，`jakarta.el-api.version` **5.0.1**，animal-sniffer 忽略 `jakarta.servlet.*`：<https://github.com/Activiti/Activiti/blob/8.8.1/pom.xml>、<https://github.com/Activiti/Activiti/releases/tag/8.8.1>
- `activiti-spring-boot-starter:8.8.1`：Maven Central **404**；Alfresco Nexus **200**：`https://artifacts.alfresco.com/nexus/content/repositories/activiti-releases/org/activiti/activiti-spring-boot-starter/8.8.1/`（仓库 URL 亦见 gitbook Releases）。

8.8.1 把命名空间从 `javax` 换到 `jakarta`，但停在 **Boot 3.5 / Framework 6 一代**（EL API 5.0 = Jakarta EE 9/10 级），**不是** Framework 7 / Servlet 6.1 / EE 11。

### 4.2 Boot 4 工作：合入 develop，仅 alpha / rc

- PR [Activiti#5331](https://github.com/Activiti/Activiti/pull/5331)「AAE-43225 Bump Spring Boot to 4.0.4」于 **2026-04-22 合入 `develop`**。
- 调研时 `develop` 根 POM：`java.version` **25**，`spring-boot.version` **4.0.7**，`jakarta.el-api.version` **6.0.1**（EE 11 EL），忽略 `jakarta.servlet.*`：<https://github.com/Activiti/Activiti/blob/develop/pom.xml>
- Tag **`9.1.0-alpha.61`**（GitHub prerelease，2026-08-11）同一组属性；Alfresco Nexus 上该版本 starter **存在**（HTTP 200）。
- Tag **`7.21.0-rc.524`**（GitHub `prerelease: true`，2026-08-18）根 POM 同样是 Boot **4.0.7** / Java **25**；Alfresco 路径 `…/activiti-spring-boot-starter/7.21.0-rc.524/` 返回 **404**（GitHub 打了 rc tag，不等于该字符串已进官方 releases 仓库）。
- 没有 gitbook / 官方「升级到 Spring Boot 4」迁移页（8.2.0 之后只指向 GitHub Releases）。
- 这些线上把 **Java 编译目标设为 25**，高于地图票的 JDK 21 目标；即便取 alpha，也不构成「官方支持的 GA 版本 + 迁移指南」。

**结论（Activiti 路径）：** 可维护（GA、文档化坐标、与 Boot 4 对齐）的升级路径 **不存在**。最近的 GA 是 Boot 3；Boot 4 停在 alpha/rc/`develop`。

---

## 5. 若 Activiti 跟不上：Flowable 对 Boot 4 的事实

Flowable 官方用户指南写明：**「Flowable is a fork of Activiti」**，包名等改为 `flowable`：<https://www.flowable.com/open-source/docs/bpmn/ch02-GettingStarted>

以下只列版本与坐标，不评价是否该换。

### 5.1 Flowable 8.0.0（GA，Maven Central）

- GitHub Release **2026-02-27**，tag `flowable-8.0.0`：Breaking Changes 第一条为 **Upgrade to Spring Framework 7 and Spring Boot 4**；并写 **「There is no support for Spring Boot 3 anymore.」**：<https://github.com/flowable/flowable-engine/releases/tag/flowable-8.0.0>
- Maven Central metadata：`org.flowable:flowable-spring-boot-starter` 的 `<latest>` / `<release>` 均为 **8.0.0**（`lastUpdated` 20260227161728）：<https://repo1.maven.org/maven2/org/flowable/flowable-spring-boot-starter/maven-metadata.xml>
- 存在的 Boot 4 starter 坐标（均 8.0.0）：
  - `org.flowable:flowable-spring-boot-starter`（全引擎聚合）
  - `org.flowable:flowable-spring-boot-starter-process`（仅 Process 引擎，含 `flowable-engine` + `flowable-spring` + `spring-boot-starter-jdbc`）
- `flowable-dependencies:8.0.0` 属性（<https://repo1.maven.org/maven2/org/flowable/flowable-dependencies/8.0.0/flowable-dependencies-8.0.0.pom>）：

  | 属性 | 值 |
  |------|-----|
  | `spring.boot.version` | **4.0.2** |
  | `spring.framework.version` | **7.0.3** |
  | `spring.security.version` | 7.0.2 |
  | `jakarta.servlet-api.version` | **6.1.0**（EE 11 Servlet） |
  | `jakarta.persistence-api.version` | **3.2.0**（EE 11 JPA） |
  | `jakarta.enterprise.cdi-api.version` | 4.1.0 |

- `flowable-parent:8.0.0`：`jdk.version` **17**（compiler `release` 17）。JDK 21 落在 Spring Boot 4 / Framework 7 官方支持范围内。
- `org.flowable:flowable-image-generator:8.0.0` 在 Central **存在**（HTTP 200）。没有 `activiti-modeler` 同名 5.x 后继。

Enterprise 文档（嵌入 starter 时）把升级描述为 **先 bump `flowable-spring-boot-starter-*`，再单独把项目的 Spring Boot 升到该 Service Pack 注明的版本**；并写明大版本常与 Spring Boot **大版本**对齐，可能需要更新的 Jakarta Servlet 容器：<https://documentation.flowable.com/latest/admin/maintenance/version-upgrade>

### 5.2 Flowable 7.x（对照：Boot 3 / 非 Boot 4）

- **7.0.0**（2023-09-21）：第一个稳定 7.x，聚焦 **Spring Boot 3、Spring 6、Java 17**；REST 应用需要 **Jakarta 9** servlet 容器；Jakarta Mail 替换 Javax Mail：<https://github.com/flowable/flowable-engine/releases/tag/flowable-7.0.0>
- **7.2.0**（2025-08-21）：Upgrade to **Spring Boot 3.5.4**；发布说明预告下一版（Flowable 8）将基于 Spring 7 / Boot 4：<https://github.com/flowable/flowable-engine/releases/tag/flowable-7.2.0>
- `flowable-dependencies:7.2.0`：`spring.boot.version` **3.5.4**，`spring.framework.version` **6.2.10**，`jakarta.servlet-api.version` **6.0.0**（EE 10 Servlet，不是 6.1）：<https://repo1.maven.org/maven2/org/flowable/flowable-dependencies/7.2.0/flowable-dependencies-7.2.0.pom>

Flowable **7.x 不是 Boot 4 路径**；**8.0.0 才是**，且与 Boot 3 互斥。

---

## 6. 对照本仓库用法：引擎 API 层面（事实，非选型）

本仓库用到的调用都是引擎服务，不依赖 iframe modeler：

- zip：`RepositoryService.createDeployment().addZipInputStream`
- 启动：`RuntimeService.startProcessInstanceByKey`
- 待办：`TaskService.createTaskQuery().taskAssignee`
- 已办：`HistoryService.createHistoricTaskInstanceQuery().finished`
- 通过：`TaskService.complete`
- 拒绝：改 `BpmnModel` 流向后 `complete`

这些类型名在 Activiti 7 与 Flowable 文档/JAR 中都存在（Flowable 包名为 `org.flowable.engine.*`）。**已发起**走业务表，与引擎列表 API 无关。`ACT_*` 表与进行中实例如何迁，地图票 **Not yet specified**，本页不展开。

---

## 7. 地图票可直接引用的 gist

Activiti 7.1.0.M6（Boot 2.1 / Java 8 / Central）与 modeler/image-generator 5.22.0 **没有** 官方 GA 的 Boot 4 / SF 7 / Jakarta EE 11 升级路径；Activiti 最新 GA 8.8.1 停在 Boot 3.5.7（Alfresco Nexus）；Boot 4 仅 alpha/`develop`。Flowable 8.0.0 GA 在 Central 提供 `flowable-spring-boot-starter`（Boot 4.0.2 / SF 7.0.3 / Servlet 6.1），且不再支持 Boot 3。
