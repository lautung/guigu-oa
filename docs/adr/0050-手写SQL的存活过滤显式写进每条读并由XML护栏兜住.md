# 手写 SQL 的存活过滤显式写进每条读，并由一条 XML 护栏兜住

#198 核出 `OaProcessMapper.selectProcessPage`（已发起与配置实例查询共用的那条读）的 `<where>`
没有 `a.is_deleted = 0`，而同文件四条兄弟读都带。根因不是谁写漏了手，是这套能力不可继承：
`@TableLogic`（`BaseEntity`）只作用于 MyBatis-Plus 生成的 SQL，手写 XML 一律不吃。#181 把行
形状收成 `approvalRowColumns`/`approvalRowJoins` 之后，这份唯一 owner 缺的正是「行本体存活」
这一条，于是新读照着 include 也能漏。

决定：**存活谓词单独成片段 `approvalRowLiveness`（`a.is_deleted = 0`），本文件 6 条语句全部
include**（已发起、待处理回查、已处理 count/行、抄送 count/行）；谓词只落在审批行本体 `a` 上，
join 侧的 `sys_user`/模板/类型不禁删——模板或用户被逻辑删除后，历史审批仍要显示当时的名字。
`selectProcessPage` 补上过滤后，分页插件从同一条 SQL 派生的 count 自动同口径，`total` 与
`records` 一起修好。配一条机械护栏 `OaProcessMapperXmlLivenessTest`：本文件每条 `<select>`
必须 include 该片段（或直接写 `a.is_deleted`），且片段本身必须就是 `a.is_deleted = 0`。断言
刻意不宽成「SQL 里出现 is_deleted」：两条 count 有 `r.`/`g.` 自己的过滤，宽断言会让本体漏过滤
的语句照样绿。护栏只覆盖这一个文件，不做全仓规则。索引不动：
`idx_oa_process_user_create_time(user_id, create_time)` 不含 `is_deleted`，把等值列插进索引会
破坏排序匹配（#185 的 hint 依赖该索引的反向扫描）；已删行数为 0 时反向扫描 + 残余过滤 + LIMIT
提前终止与加过滤前同形，不另做 EXPLAIN/压测取证。

## 考虑过的替代方案

- **只补一行、不加护栏**：注释拦不住下一个手写 select；护栏约 40 行、跑在 `mvn test` 里不新增
  门禁，且能指名是哪个 `<select>` 漏了。反过来的代价是新增不涉审批行的读会被拦——那正是要人
  停下来把存活语义写清楚的地方。
- **做成全仓 mapper XML 规则**：`oa_process_template_publication` 是刻意没有 `is_deleted` 的
  证据表（ADR 0031），全仓规则必须带豁免注释才不误伤；而全仓目前只有两个文件有真实语句
  （`SysMenuMapper.findMenuListByUserId` 已有过滤），豁免口的复杂度买不到对应覆盖面。
- **把谓词折进 `approvalRowJoins`**：`a` 的淘汰条件写进 `left join ... on` 只影响被 join 的
  那一侧（名字变 null、行还在），不是过滤行本体；语义错。
- **顺手把幂等键那半个也修了**（重放查不滤 `is_deleted`）：会让已删审批经重放渠道重新可见，
  与「已删行对读面不可见」直接冲突；而且「删除后同键重放该返回原审批还是新审批」是个需要单独
  裁决的产品问题，现在没有删除路径，答不了。

## 后果

- 用户可见变化为零：`oa_process` 今天没有任何删除路径（管理端清理、作废语义都还没有），库里
  没有 `is_deleted=1` 的审批行。这次修的是读合同的缺口，不是活动缺陷。
- **引入删除路径时必须一起处理的剩余欠账**（ADR 0032 记的「将来若引入删除，需要同时处理键的
  归属」仍成立）：`ApprovalWriter.startedProcess` 用 MyBatis-Plus 查询重放键，会自动追加
  `is_deleted = 0`；删掉一条审批后同键重放查不到原行，走插入撞
  `uk_oa_process_idempotency_key`，被翻译成「重复提交」201，**原审批永远取不回**。另外软删行
  仍带 `process_instance_id`，Flowable 实例与历史不会随之终止，待处理侧引擎仍会给出该任务的
  businessKey，只是回查 `selectRowsByIds` 会跳过它（现状不变）。
- 回归锚点：`ApprovalContractTest.已删审批行不在已发起与配置实例查询里且total同口径`
  （两入口的 `records`+`total`、keyword 与存活过滤叠加的负例、与 `selectById` 的详情口径一致）、
  `OaProcessMapperXmlLivenessTest`（本文件每条读都过滤本体存活；已用「删掉 include」的变异
  验证它确实会红并指名语句）。

Related: #198、#181、#185；ADR 0031、0032、0045。
