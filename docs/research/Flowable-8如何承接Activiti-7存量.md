# Flowable 8 如何承接 Activiti 7 存量

> 研究票：[Flowable 8 如何承接 Activiti 7 存量](https://github.com/lautung/guigu-oa/issues/30)  
> 地图票：[升级到 Spring Boot 4 与 Vue 3 的依赖规格](https://github.com/lautung/guigu-oa/issues/22)  
> 引擎锁：[审批引擎跟不上 Boot 4 时规格怎么写](https://github.com/lautung/guigu-oa/issues/29) 已锁 `org.flowable:flowable-spring-boot-starter-process:8.0.0`，Boot 4.1.0 parent 不变，不 import Flowable BOM。  
> 来源：本仓库 `server/guigu-oa-parent`；Flowable / Activiti 官方文档与 GitHub tag `flowable-8.0.0` / `7.1.0.M6` 引擎源码；Maven Central。
> 说明：只报官方文档与引擎源码事实，不推荐进行中审批怎么迁。在线 iframe 设计器不在范围。

---

## 结论

规格锁的 Flowable 8.0.0 **Process 引擎仍用 `ACT_*` 表**（另有少量 `FLW_RU_BATCH*` 公共表）。官方**没有**「Activiti 7 → Flowable 8」升级指南。唯一成文的迁移指南是 **Activiti / Flowable v5 → Flowable V6**，兼容模式也只覆盖 **v5 工件**。

本仓库 Activiti `7.1.0.M6` 建库时写入的 `ACT_GE_PROPERTY.schema.version` 是字符串 **`7.1.0-M6`**。Flowable 8.0.0 的已知版本列表里没有这一条；对不上时 `databaseSchemaUpdate=true` **不会**把它当 Flowable 7.1 升级，而是抛 `unknown version from database`。因此：**既有 `ACT_*` 行与进行中实例，没有文档化的自动迁移路径。**

已发布的 **流程 zip**（`xmlns:activiti` / `activiti:assignee` / `activiti:candidateUsers`）在 Flowable 8 **重新部署时仍会解析**：文档写明支持 `activiti:` 前缀，8.0.0 转换器按 `http://activiti.org/bpmn` 读属性、按元素 **local name** 读 `taskListener`。这只覆盖 **再 deploy 的 XML**，不覆盖旧库里的 runtime 行。

`oa_process.process_instance_id` 是 `VARCHAR(100)`，两边默认 ID 都是 **time-based UUID 字符串**（`varchar(64)` 列）。官方把实例 id 当不透明主键、按精确字符串查询。**格式本身没有文档化的断裂**；存量衔接是否成立，取决于引擎能否读到旧 `ACT_RU_EXECUTION.ID_`，而自动 schema 升级对 `7.1.0-M6` 没有已知版本入口。

---

## 1. 本仓库实际用法（对照范围）

后端 `service-oa` 现用 `org.activiti:activiti-spring-boot-starter:7.1.0.M6`（`server/guigu-oa-parent/service-oa/pom.xml`）。`application-dev.yml`：

| 键 | 值 |
|----|----|
| `spring.activiti.database-schema-update` | `true` |
| `spring.activiti.db-history-used` | `true` |
| `spring.activiti.history-level` | `full` |
| `spring.activiti.check-process-definitions` | `true` |

`sql/guigu-oa-init.sql` 写明 `ACT_*` 由引擎首启自动建，不随业务脚本一起发。

| 产品能力 | 实现 | 引擎 API |
|----------|------|----------|
| 审批模板发布流程 zip | `OaProcessTemplateServiceImpl.publish` → `deployByZip` | `RepositoryService.createDeployment().addZipInputStream(...).deploy()` |
| 发起 | `startUp` | `RuntimeService.startProcessInstanceByKey(key, businessKey, variables)`；`oa_process.processInstanceId = processInstance.getId()` |
| 待处理 | `findfindPending` | `TaskService.createTaskQuery().taskAssignee(username)`，再用 `task.getProcessInstanceId()` 查实例、取 `businessKey` |
| 已处理 | `findProcessed` | `HistoryService.createHistoricTaskInstanceQuery().taskAssignee(...).finished()`，按 `processInstanceId` 扫 `oa_process` |
| 已发起 | `findStarted` | **不查引擎**，按当前用户扫 `oa_process` |
| 通过 | `approve` `status == 1` | `TaskService.complete` |
| 拒绝 | `endTask` | 改当前节点 outgoing 到 `EndEvent` 再 `complete` |

仓库内 BPMN 示例（`service-oa/src/main/resources/process/*.bpmn20.xml`）一律 `xmlns:activiti="http://activiti.org/bpmn"`，使用 `activiti:assignee`、`activiti:candidateUsers`、`activiti:taskListener`。产品主路径是 zip 上传，不是 classpath 自动部署。iframe 设计器不在本票范围。

---

## 2. 表前缀与 schema

### 2.1 Process 引擎仍是 `ACT_*`

Flowable 开源用户指南「Database table names explained」写明：库表名都以 **`ACT_`** 开头；`RE` 仓库、`RU` 运行时、`HI` 历史、`GE` 通用。运行时行在实例结束后删除。

来源：<https://www.flowable.com/open-source/docs/bpmn/ch03-Configuration>（章节 Database table names explained）

tag `flowable-8.0.0` 的 MySQL **engine** 建表脚本仍是 `ACT_RE_DEPLOYMENT`、`ACT_RU_EXECUTION`、`ACT_RE_PROCDEF` 等：

<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/resources/org/flowable/db/create/flowable.mysql.create.engine.sql>

同 tag 的 **common** 脚本在 `ACT_GE_*` / `ACT_RU_TASK` / `ACT_HI_TASKINST` / job 表之外，另有：

- `FLW_RU_BATCH`
- `FLW_RU_BATCH_PART`

来源：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/resources/org/flowable/common/db/create/flowable.mysql.create.common.sql>

`flowable-spring-boot-starter-process:8.0.0` 的 POM 只带 Process 引擎 + JDBC starter，**不**带 event-registry。Maven Central：<https://central.sonatype.com/artifact/org.flowable/flowable-spring-boot-starter-process/8.0.0>  
因此 Process starter 不会为 CMMN/DMN/事件注册表建 `FLW_EVENT_*` 一类表；Process 运行时主表仍是 `ACT_*`，外加上述 `FLW_RU_BATCH*`。

### 2.2 `schema.version` 字符串对不上

Activiti `7.1.0.M6` MySQL engine 建表脚本插入：

```sql
insert into ACT_GE_PROPERTY values ('schema.version', '7.1.0-M6', 1);
```

来源：<https://github.com/Activiti/Activiti/blob/7.1.0.M6/activiti-core/activiti-engine/src/main/resources/org/activiti/db/create/activiti.mysql.create.engine.sql>

Flowable 8.0.0 当前版本常量是 **`8.0.0.0`**。`FLOWABLE_VERSIONS` 含 5.7…5.24、虚拟 `5.99.0.0`、6.x、7.0.0.0…7.2.0.2、最后 `8.0.0.0`。其中的 `7.1.0.0` / `7.1.0.1` / `7.1.0.2` 是 **Flowable 自己的 7.1 线**，列表里 **没有** `7.1.0-M6`。

`FlowableVersion.matches` 是 **字符串全等**（另加显式 alternative 列表，例如 5.12 才有 `5.12.1`）。`7.1.0-M6` 不等于 `7.1.0.0`。

未知版本时 `getFlowableVersionIndexForDbVersion` 只对 **`5.` 前缀** 回落到 `LAST_V5_VERSION`；否则：

```text
Could not update Flowable database schema: unknown version from database: '<dbVersion>'
```

来源：

- <https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/FlowableVersions.java>
- <https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/FlowableVersion.java>

`EngineSqlScriptBasedDbSchemaManager.schemaUpdateInLock`：若引擎表已存在（如 `ACT_RU_EXECUTION`），就读 `ACT_GE_PROPERTY` 的 dbVersion，再交给上面的版本索引。因此 **`databaseSchemaUpdate=true` 碰上已有 Activiti 7.1.0-M6 库时，走的是「未知版本」路径，不是「从 7.1.0.0 升到 8.0.0.0」**。

来源：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/db/EngineSqlScriptBasedDbSchemaManager.java>

发行包升级 SQL 按 **Flowable** 版本步进（…`7.1.0.to.7.2.0`、`7.2.0.to.8.0.0`），文件名里 **没有** Activiti 或 `7.1.0-M6`。

目录：<https://github.com/flowable/flowable-engine/tree/flowable-8.0.0/distro/sql/upgrade/all>

### 2.3 同名 `ACT_*` 列已经分叉

两边都叫 `ACT_RU_EXECUTION` / `ACT_RU_TASK` / `ACT_RE_DEPLOYMENT`，列集合不是同一套。与本仓库读写相关的差异（MySQL create 脚本对照）：

| 表 | Activiti 7.1.0.M6 | Flowable 8.0.0 |
|----|-------------------|----------------|
| `schema.version` | `7.1.0-M6` | `8.0.0.0` |
| `ACT_RE_DEPLOYMENT` | 有 `VERSION_`、`PROJECT_RELEASE_VERSION_` | 有 `DERIVED_FROM_`、`DERIVED_FROM_ROOT_`、`PARENT_DEPLOYMENT_ID_`；无前两列 |
| `ACT_RU_EXECUTION` | 有 `APP_VERSION_` | 无 `APP_VERSION_`；有 `START_ACT_ID_`、`LOCK_OWNER_`、`EXTERNAL_WORKER_JOB_COUNT_`、`CALLBACK_*`、`REFERENCE_*`、`BUSINESS_STATUS_` 等 |
| `ACT_RU_TASK` | 有 `BUSINESS_KEY_`、`APP_VERSION_` | 无这两列；有 `SCOPE_*`、`STATE_`、`IN_PROGRESS_*`、`CLAIMED_BY_`、计数列等 |
| `ACT_HI_TASKINST` | 无 `SCOPE_*` / `STATE_` 等 | 有 `SCOPE_*`、`STATE_`、`IN_PROGRESS_*`、`COMPLETED_BY_` 等（在 common 脚本） |
| 仅 Activiti 7 | `ACT_RU_INTEGRATION` | 无此表 |
| 仅 Flowable 8 common | — | `FLW_RU_BATCH`、`FLW_RU_BATCH_PART`、`ACT_RU_HISTORY_JOB`、`ACT_RU_EXTERNAL_JOB` 等 |

来源：上引 Activiti `activiti.mysql.create.engine.sql`、`activiti.mysql.create.history.sql`；Flowable `flowable.mysql.create.engine.sql`、`flowable.mysql.create.common.sql`。

---

## 3. `spring.activiti.*` → `flowable.*`

Activiti 7.1.0.M6 starter：`@ConfigurationProperties("spring.activiti")`。

<https://github.com/Activiti/Activiti/blob/7.1.0.M6/activiti-core/activiti-spring-boot-starter/src/main/java/org/activiti/spring/boot/ActivitiProperties.java>

Flowable 8.0.0 autoconfigure：`@ConfigurationProperties(prefix = "flowable")`。

<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-spring-boot/flowable-spring-boot-starters/flowable-spring-boot-autoconfigure/src/main/java/org/flowable/spring/boot/FlowableProperties.java>

开源 REST 应用文档把 Spring Boot 属性列成 `flowable.database-schema-update`、`flowable.history-level`（旧名 `engine.process.schema.update` / `engine.process.history.level`）。

<https://www.flowable.com/open-source/docs/bpmn/ch13-Applications>

与本仓库已写配置的对应关系（Spring Boot 松散绑定：kebab-case = 字段名）：

| 本仓库 `spring.activiti.*` | Activiti 7 Boot 默认 | Flowable 8 等价键 | Flowable 8 Boot 默认 |
|---------------------------|----------------------|-------------------|----------------------|
| `database-schema-update` | `"true"` | `flowable.database-schema-update` | `"true"` |
| `db-history-used` | **`false`** | `flowable.db-history-used` | **`true`** |
| `history-level` | **`NONE`** | `flowable.history-level` | **`AUDIT`** |
| `check-process-definitions` | `true` | `flowable.check-process-definitions` | `true` |

引擎 XML 侧 `databaseSchemaUpdate` 取值（非 Boot 特有）：`false` 校验版本不匹配则抛；`true` 缺表创建、版本落后则升级；`create-drop` 启建停删。

来源：<https://www.flowable.com/open-source/docs/bpmn/ch03-Configuration>（Database configuration / Database upgrade）

`history` 配置示例值为 `audit`（「History configuration」同一页）。`HistoryLevel` 枚举：`none` / `instance` / `task` / `activity` / `audit` / `full`。

<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/history/HistoryLevel.java>

本仓库把 Activiti 7 的 Boot 默认（历史关、`NONE`）改成了 `db-history-used: true` + `full`，因为已处理走 `HistoricTaskInstanceQuery.finished()`。Flowable 8 Boot 默认已是开历史、`AUDIT`；`full` 仍须显式设 `flowable.history-level=full` 才与现网一致。`AUDIT` 是否够已处理查询，官方只定义了级别含义，没有对本仓库查询的保证。

自动部署目录默认值不同（产品主路径是 zip，但键仍存在）：

- Activiti 7：`classpath*:/**/processes/`
- Flowable 8：`classpath*:/processes/`

---

## 4. 进行中实例与历史任务：官方路径停在 v5

开源站点「Flowable Migration」原文：指南用于 **Flowable 或 Activiti v5 升到 Flowable v6**；「对多数人只需改包名」；**没有单独的 Activiti 7 章节**。

<https://www.flowable.com/open-source/docs/>  
<https://www.flowable.com/open-source/docs/migration>

该指南对 v5→V6 写明：

- 库 schema 视为同一条线，V6 首次启动做自动更新（job 表拆分等）。
- **进行中实例可以留在库里**。
- 核心执行模型重写后，用嵌入的 **v5 引擎** 跑仍标成 v5 的定义。
- **默认关闭**；开启：`flowable5CompatibilityEnabled=true`，并加 `flowable5-compatibility` JAR。
- 迁到 V6 模式的办法是 **重新部署** 定义：新实例走 V6，旧实例仍走 v5。

tag `flowable-8.0.0` 仍带模块 `flowable5-compatibility` / `flowable5-engine`。`ProcessEngineConfigurationImpl` 注释：`Default flowable 5 backwards compatibility is disabled!`

没有文档或版本列表把 **Activiti 7.1.0-M6 的部署 / 运行时行** 标成 v5 工件。指南里的兼容模式针对 **v5 标签**，不是 Activiti 7。

`FLOWABLE_VERSIONS` 另有 `CAM_MIGRATION_VERSIONS`（部分 Camunda 版本号）。那是 Camunda 迁移入口，**不是** Activiti 7.1.0-M6。

用户指南「Database upgrade」说：把 **较旧的 Flowable 库** 指给新库、设 `databaseSchemaUpdate=true`，会升到当前版本。前提是 `schema.version` 能被 `FlowableVersions` 认出来。`7.1.0-M6` 认不出来（见 §2.2）。官方 **没有** 写成「不支持 Activiti 7 运行时」的专页；源码行为是 **未知版本则拒绝更新**。发行物里也 **没有** Activiti 7 专用 migrator。

---

## 5. 已发布流程 zip：`activiti:` 命名空间在部署时仍解析

用户指南 BPMN 扩展一节：自定义扩展用 **`flowable:`** 前缀；并写 **「Flowable engine also supports the activiti: namespace prefix.」**

<https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs>

8.0.0 常量：

```text
FLOWABLE_EXTENSIONS_NAMESPACE = "http://flowable.org/bpmn"
ACTIVITI_EXTENSIONS_NAMESPACE = "http://activiti.org/bpmn"
ATTRIBUTE_TASK_USER_ASSIGNEE = "assignee"
ATTRIBUTE_TASK_USER_CANDIDATEUSERS = "candidateUsers"
ELEMENT_TASK_LISTENER = "taskListener"
```

<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-bpmn-converter/src/main/java/org/flowable/bpmn/constants/BpmnXMLConstants.java>

`BpmnXMLUtil.getAttributeValue` 依次读 Flowable → Activiti → Camunda 命名空间。因此 `activiti:assignee` / `activiti:candidateUsers`（`xmlns:activiti="http://activiti.org/bpmn"`）在 **parse 部署物** 时会被读到。

子元素按 **`XMLStreamReader.getLocalName()`** 分派：`activiti:taskListener` 的 local name 是 `taskListener`，走 `TaskListenerParser`。

来源：

- <https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-bpmn-converter/src/main/java/org/flowable/bpmn/converter/util/BpmnXMLUtil.java>
- <https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-bpmn-converter/src/main/java/org/flowable/bpmn/converter/UserTaskXMLConverter.java>
- <https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-bpmn-converter/src/main/java/org/flowable/bpmn/converter/child/TaskListenerParser.java>

这是 **部署时 XML 解析** 的事实。它不把旧库 `ACT_RE_*` / `ACT_RU_*` 自动变成 Flowable 8 可升级 schema。写出时转换器写的是 `flowable:` 前缀（`writeQualifiedAttribute`）；读入仍认 `activiti:`。

仓库 zip 部署 API（`addZipInputStream`）两边都是 `RepositoryService` 部署入口；Flowable 8 包名是 `org.flowable.engine.RepositoryService`（v5 指南中的包重命名）。

---

## 6. `oa_process.processInstanceId` 与引擎 id

业务列：`oa_process.process_instance_id VARCHAR(100)`（`sql/guigu-oa-init.sql`）。  
`startUp` 写入 `ProcessInstance.getId()`。待处理用 `task.getProcessInstanceId()`；已处理用历史任务的 `processInstanceId` 反查该列。

引擎侧：

- 实例 / 执行 / 任务主键列均为 **`varchar(64)`**（Activiti 7 与 Flowable 8 的 create 脚本一致）。
- `ProcessInstance` 继承 `Execution`。`Execution.getId()` 是执行 id；若该执行就是流程实例，**与 process instance id 相同**。

<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/runtime/Execution.java>

ID 生成：

| 引擎 | 默认 |
|------|------|
| Activiti 7.1.0.M6 Spring Boot | `useStrongUuids = true`，配置里 `setIdGenerator(new StrongUuidGenerator())` |
| Flowable 8.0.0 | `AbstractEngineConfiguration.initIdGenerator()`：未设置则 `new StrongUuidGenerator()`；实现为 FasterXML **time-based UUID** `.toString()` |

来源：

- <https://github.com/Activiti/Activiti/blob/7.1.0.M6/activiti-core/activiti-spring-boot-starter/src/main/java/org/activiti/spring/boot/ActivitiProperties.java>
- <https://github.com/Activiti/Activiti/blob/7.1.0.M6/activiti-core/activiti-spring-boot-starter/src/main/java/org/activiti/spring/boot/ProcessEngineAutoConfiguration.java>
- <https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/AbstractEngineConfiguration.java>
- <https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/persistence/StrongUuidGenerator.java>

36 字符 UUID 落在 `VARCHAR(100)` 与引擎 `varchar(64)` 内。官方没有「换引擎会改写已有实例 id」的说明；查询是按 id 精确匹配。

因此：**文档化的 id 格式 / 相关方式没有单独断裂点。** 存量 `oa_process.processInstanceId` 能否继续对上待处理 / 已处理，取决于 Flowable 8 能否读到旧 `ACT_RU_EXECUTION` / `ACT_HI_TASKINST` 里同一主键。§2–§4 表明自动 schema 升级 **不认** `7.1.0-M6`。已发起只扫 `oa_process`，不依赖引擎 id 解析。

---

## 7. 官方 Activiti → Flowable 指南覆盖范围

| 文档 | 覆盖 | 不覆盖 |
|------|------|--------|
| [Flowable Migration Guide : Flowable or Activiti v5 to Flowable V6](https://www.flowable.com/open-source/docs/migration) | v5 包名 `org.activiti`→`org.flowable`；v5 库可留；`flowable5CompatibilityEnabled` | Activiti **7.1.0.M6** 的 schema 字符串、运行时模型、zip 存量库 |
| [Database upgrade](https://www.flowable.com/open-source/docs/bpmn/ch03-Configuration) | 已知 Flowable 版本之间 `databaseSchemaUpdate=true` | 未知 `schema.version` |
| [BPMN Constructs](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs) | 部署 XML 时认 `activiti:` 前缀 | 运行时表迁移 |
| `flowable5-compatibility`（8.0.0 仍在源码树） | 标成 v5 的定义 | Activiti 7 部署 |

---

## 来源索引

1. Flowable OSS 迁移入口：<https://www.flowable.com/open-source/docs/> 与 <https://www.flowable.com/open-source/docs/migration>
2. Flowable 配置 / 表名 / schema 升级 / history=`audit`：<https://www.flowable.com/open-source/docs/bpmn/ch03-Configuration>
3. `activiti:` 前缀：<https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs>
4. Spring Boot `flowable.*` 属性表：<https://www.flowable.com/open-source/docs/bpmn/ch13-Applications>
5. Flowable 8.0.0 版本列表与匹配：`FlowableVersions.java` / `FlowableVersion.java`（tag `flowable-8.0.0`）
6. Schema 更新入口：`EngineSqlScriptBasedDbSchemaManager.java`（同上 tag）
7. Flowable 8.0.0 MySQL create：`flowable.mysql.create.engine.sql`、`flowable.mysql.create.common.sql`
8. Activiti 7.1.0.M6 MySQL create：`activiti.mysql.create.engine.sql`（`schema.version='7.1.0-M6'`）、`activiti.mysql.create.history.sql`
9. Boot 属性：`ActivitiProperties`（`spring.activiti`）与 `FlowableProperties`（`flowable`）
10. BPMN 解析：`BpmnXMLConstants`、`BpmnXMLUtil.getAttributeValue` / `parseChildElements`、`UserTaskXMLConverter`
11. UUID：两边 `StrongUuidGenerator`；Activiti Boot `useStrongUuids=true`
12. Maven Central：`org.flowable:flowable-spring-boot-starter-process:8.0.0`
13. 本仓库：`service-oa/pom.xml`、`application-dev.yml`、`OaProcessServiceImpl`、`oa_process.process_instance_id`、`resources/process/*.bpmn20.xml`
