# 审批列表读收成一个读 module，行形状/窗口/回查/排序一处拥有

第三轮架构审查（#174）核出的读热区，经规格 #178 收敛：同一个审批行形状有五套读实现——
办事四个列表（待处理/已处理/已发起/抄送我的）与配置侧审批实例查询各自取数。列清单写两遍、
分页 offset 手算三遍、待处理/已处理逐行回查账本（第一个 N+1），引擎侧还逐任务查 businessKey
（第二个 N+1）；已发起与配置实例查询没有显式 ORDER BY，「翻不到刚发起的审批」一类坑的根因仍在
（#142）。对用户可见的则是待处理/已处理卡片缺发起人与审批类型，五列表行内容口径不一致。

决定：新增 `process/read/ApprovalListReader`（interface，五个具名方法
`pending`/`processed`/`started`/`carbonCopied`/`instanceQuery`，参数强类型、不设 kind 字符串）
作为五种审批列表读的唯一入口，唯一实现 `LedgerApprovalListReader`；本 interface 只读，不写
引擎与账本。行形状、窗口算术、回查批量化、排序在这一个 module 里一处拥有。旧门面
`OaProcessService` 删掉四个读方法声明；两个控制器（`OaProcessController`/`ProcessController`）
与演示数据播种器 `DemoScenarioSeeder` 全部改注入新 module——五个列表入口走同一扇门。抄送服务
只剩写侧（发起抄送、任务规则抄送）与审批成员资格扩展，列表读连着手写 COUNT/SELECT/逐字段映射
搬进 `OaProcessMapper`（`COUNT(DISTINCT)` 的 total=去重审批数语义原样保留），对抄送列表读的
Mockito 直测随之消亡，行为由合同缝接管。

行形状唯一 owner 是 `OaProcessMapper.xml` 的一份共享列清单（`approvalRowColumns`/
`approvalRowJoins`：列、别名、三个 left join），四条行取数全部 include（抄送的 total 是独立的
count 聚合）；`ProcessVo` 是唯一行类型。补齐：待处理/已处理行补发起人姓名（`name`）、审批模板名
（`processTemplateName`）、审批类型名（`processTypeName`），办事卡片开始显示（additive）。删除：
从未被任何生产者填充、五列表零消费的 `formProps`/`formOptions`，以及配置查询 VO 上两个未被
SQL 消费的过滤死字段（`processTemplateId`/`processTypeId`）。保留：`update_time` 进全部行形状
（公共字段口径，ADR 0031），抄送行随之补上。读侧对 `description` 原样透传、不做读侧订正
（ADR 0043 的订正是一次性迁移，不进读路径）。

窗口算术归 `PageWindow`（#180，ADR 0024）：pending/processed 的 `firstResult` 与抄送的 `offset`
都从它派生，三处手算消失；分页上限拒绝语义不变（GLOSSARY「分页上限」）。

待处理/已处理**保持引擎分页**：行集与 total 仍由引擎的 `pendingTasks`/`pendingCount`/
`processedTasks`/`processedCount` 分页与计数（任务创建时间倒序），本 module 只在拿到当页任务后
按页批量回查账本行（`selectRowsByIds`/`selectRowsByInstanceIds`）并按引擎顺序重排；同刻 tiebreaker
（任务 id 决胜）在 port 实现内部补，不另设排序语义。不把引擎表 join 进账本 SQL：`ACT_*` 是
Flowable 按自己的 schema 机制自建自迁移的内部表（#158 的坑史就在这层，Activiti 7 → Flowable 8
切库已经动过一次引擎表），账本 mapper 与它耦合后引擎升级要连着改业务 SQL；ADR 0019 的红线
（不绕引擎、port 不暴露引擎类型）也直接排除它。引擎侧的第二个 N+1 同样收在 port 实现内：
`ProcessEngineApproval.businessKeys` 按实例 id 集合一次取回，带 `PageWindow.MAX_LIMIT` 护栏
（调用方只应传整页回查集合，超限显式拒绝）；`ApprovalEngine` 签名不动（ADR 0019）。

已发起与配置实例查询共用一条 `selectProcessPage`，显式 `ORDER BY create_time DESC, id DESC`
（#179，用户可见反转：最新在前，与抄送我的对齐，同刻由审批 id 决胜）。#142 安全扫描
（`scripts/security-scan.mjs`）先读第一页即命中刚发起的审批，根因关闭，扫描内注释随新顺序更新。
已知代价：两端点因此引入 filesort 且排序列无索引可走，p95 回归 6.7×/2.2×，索引层优化 Out of
Scope，已立 #185。

钉住不修的现状：已处理行=已完成任务——同一审批处理过多个节点会重复出现，total=任务数不是
审批数，`ApprovalContractTest` 用同人两节点场景钉住；行单位修正（按审批去重）另立 needs-triage
issue。待处理回查跳过空 businessKey 与已删/缺失账本行的边界行为不变。

## 考虑过的替代方案

- **join 引擎表（pending/processed 改成账本表 join `ACT_RU_TASK`/`ACT_HI_TASKINST` 一条 SQL
  取齐）**：能消掉回查、让排序一次到位，但把 Flowable 的内部 schema 当成自己的读模型——`ACT_*`
  的列与索引不是本仓库的合同，引擎升级或再切库时业务 mapper 要连着改；ADR 0019 的红线（不绕
  引擎、port 不暴露引擎类型）要求引擎读证据走 port。引擎的任务分页、计数与排序语义（任务创建
  时间倒序、total=任务数）原样保留在 port 后面，重写一遍只会再造一次语义漂移。
- **一个 kind 字符串参数把五方法收成一方法**：调用点少写几个方法名，代价是过滤组合靠运行期
  校验、编译期不再排除不可能的组合（规格 user story 13）；五个方法名本身就是文档。
- **行形状按列表各留各的列清单**：给行加一个摘要字段要改五处（规格 user story 12），列漂移
  根因原样保留。
- **已处理现在就去重**：行单位修正是用户可见的行为变化，得先改钉住现状的合同测试、再裁决
  GLOSSARY「已处理」词条（审批粒度）与现行行单位（任务粒度）的张力，另立 issue，不混进这次
  重构。

## 后果

- 用户可见变化全是 additive：待处理/已处理卡片补三个名字字段；已发起与配置实例查询从无显式
  顺序变为创建时间倒序。死字段删除（`formProps`/`formOptions` 与配置查询两个过滤死字段）是
  唯一减法，五列表零消费；响应包封不变。
- 合同缝钉住（`ApprovalContractTest`，@SpringBootTest + H2 + 内存 Redis + Flowable + MockMvc）：
  五端点行形状一致、排序、窗口、过滤（配置查询 keyword/status/时间过滤从零测试补齐）、已处理
  行单位（同人两节点重复出现、total=任务数）、抄送 total=去重审批数（同一审批多种来源只计一次）。
- 回归锚点：#142 安全扫描（`scripts/security-scan.mjs`，findStarted 第一页命中刚发起的审批）；
  k6 before/after 证据 `docs/test/evidence/2026-11-01-perf-read-module/`，执行记录
  `docs/test/execution/2026-11-01-perf-read-module.md`——混合吞吐 +29.1%（433→558.9 req/s）、
  findPending p95 −40%、findProcessed −56%，N+1 消除收益有本地证据。
- 已知回归：findStarted/配置实例查询因显式排序无索引支持 p95 回归 6.7×/2.2×，已立 #185
  （EXPLAIN 取证在证据目录 `explain-findStarted-orderby.txt`）。
- 服务端测试 471、客户端 511、接口套件 76、e2e 145、栈回归 28 全绿。
- 下一处给审批行加字段（如摘要）只改 `approvalRowColumns` 一份列清单，五种列表同时生效；改
  五列表的排序或窗口规则只动 `LedgerApprovalListReader` 一处。

**2026-11-06 修订（#271）**：待处理从引擎 assignee 单源改为三源合成（引擎处理人、候选人、加签任务），每人每审批一行；行形状扩动作类别、挂起与催办标记，仍由共享列清单唯一拥有；total 仍按合成行数。见 ADR 0068。

**2026-11-11 修订（C3，ADR 0076）**：待处理与单行读的实现从 `LedgerApprovalListReader` 迁到 `process/pending/PendingWorklist`；`ApprovalListReader` 五方法门面与 `ProcessVo` 行类型不变，三源合成、AFTER 排除、BEFORE 挂起与 total 归新 module 一处拥有。

Related: #178、#179、#180、#181、#182；ADR 0019、0024、0031、0043；GLOSSARY「审批」「已处理」「分页上限」。
