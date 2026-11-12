# 待处理与当前处理人收成一个 module，催办对象等于待处理名单

2026-11-11 架构审查（C3，快照 `b7ef156`；C1/C2 已由 ADR 0074/0075 落地）核出审批热区的同构：待处理 = 引擎 assignee + 引擎候选人 + 未决加签任务。这套三源合成在四处各写一遍，而它们必须永远一致：

- 待处理页读 `LedgerApprovalListReader.pending`（65–163）与单行读 `pendingRow`（166–221）：两源分页取数、AFTER 批次排除、BEFORE 挂起标记、`total` 口径、合并排序各一份；
- 催办对象 `ApprovalWriter.urgeTargetNames`（578–594）：引擎当前任务 + PENDING 加签任务、用户名去重、显示名；
- 成员资格 `ApprovalMembershipResolver.resolve`（44–78）：同一对来源，再算一遍本人的引擎任务、加签任务与挂起集合。

姓名投影另有两份实现（`ApprovalWriter.displayName` 759–764、`BusinessApprovalReader.displayName` 91–96），连接格式三处（等待文案 `,`、摘要/催办 `、`、加签文案 `,`）。`ApproverNames` 自称等待文案/催办/业务摘要的共用口径，实际只被 `ApprovalWriter` 与 `BusinessApprovalReader` 使用，待处理读完全没引用（审查报告称「只被催办与等待文案调用、读侧零引用」不成立——业务摘要读在用）。

同构已经漂移：待处理列表排除 AFTER 批次的原处理人（`LedgerApprovalListReader.java:115–130`），催办名单却无条件收引擎 assignee（`ApprovalWriter.java:580–594`）——同一个审批，催办会催一个已经记账通过的人；等待文案与业务摘要又只吃引擎任务、不含加签人（`ApprovalWriter.java:750–756`、`BusinessApprovalReader.java:86–96`）。

决定：把「待处理 = 三源合成」收成一个 module，所有消费者从它取。

## 决定

- **module 与落点**：新增 `com.lautung.oa.process.pending` 包与 `@Service PendingWorklist`。它拥有三源取数（引擎 `ApprovalEngine.pendingTasks`/`pendingCount`/`currentTasks` + 新加签任务证据 port）、并集与每人每审批一行、AFTER 批次排除、BEFORE 挂起标记、`total` 口径、合并排序与窗口、`pendingRow` 的选择规则、姓名投影与显示顺序。`ApprovalListReader` 的五方法门面与 `ProcessVo` 行类型都不变（ADR 0045），`pending`/`pendingRow` 改为委托；`LedgerApprovalListReader` 只留其余四种列表。
- **公开面**：`pending(Page<ProcessVo>, SysUser)`、`pendingRow(SysUser, Process)`、`handlers(Process)`、`pendingHandlerNames(Process)`。实现注记（#298）：另有窄重载 `pendingHandlerNames(Long processId, String processInstanceId)`，与 `pendingHandlerNames(Process)` 同义，供只持有 `ProcessVo` 的 `BusinessApprovalReader` 使用，避免为投影伪造 `Process` 壳。
- **处理人条目**：`HandlerEntry(username, userId, source, engineTaskId, signTaskId, suspended, afterExcluded, createTime)`；`source` 为 `ENGINE_ASSIGNEE | ENGINE_CANDIDATE | ADD_SIGN`。`handlers` 只回答事实，不解析姓名；加签条目的 `username` 可空、靠 `userId` 标识。`pendingHandlerNames` 才做「用户名→显示名」的解析，加签人按 `userId` 一次批查取用户名，不逐条查。
- **姓名与顺序**：显示名的唯一实现收进 module（注入 `SysUserService`；找不到用户或空名退回用户名）。顺序 = 引擎任务序（每任务 assignee 先、候选人后）+ PENDING 加签任务按 id；按 username 去重。连接符留在各消费方（等待文案 `,`，摘要/催办 `、`），不进 module。
- **待处理名单与催办对象同源**：待处理名单 = 未被 AFTER 批次排除的引擎条目 + 加签任务条目；BEFORE 挂起原处理人保留（行上带挂起标记）。催办对象就是这份名单的显示名——**后加签已记账通过的原处理人不再出现在催办对象里**。等待文案与业务摘要的「当前审批人」也吃同一投影；正常流转下等待文案输出不变（加签批次期间的文案本由 `addSignWaitingDescription` 接管），业务摘要在加签期间会多出加签人。
- **`pendingRow` 规则原样冻结**：引擎源优先；引擎任务命中但处于 AFTER 批次时直接返回 `null`，**不落到加签源**；否则取本人最新一条 PENDING 加签任务（id 倒序）。`urged` 仍是 process 级「最近 24 小时有 URGE 账本行」。
- **加签任务证据 port**：新增 `PendingSignTaskEvidence`（用户级分页与计数、按审批取未决快照、按 engineTaskId 批查、按类型取全部未决批次），`OaProcessAddSignTaskMapper` 只在 adapter 后。`MembershipLedgerEvidence.pendingSignTasks` 删除，避免同表第二条读缝。
- **成员资格最后接入**：`ApprovalMembershipResolver` 改为消费原始 `handlers(process)`，自己按 username/userId 做集合交集并保留 `canAct`/挂起语义；`handlers` 不解析姓名，`canAct` 热路径不新增用户查询。权限语义不变，接入步骤重跑 security/合同套件。
- **删除重复**：`ApproverNames` 删除，`MAX_IDEMPOTENCY_KEY_LENGTH` 移到 `ProcessStartSupport`；`ApprovalWriter`/`BusinessApprovalReader` 两份私有 `displayName` 删除。
- **不改表结构、不新增迁移、不改客户端与服务端响应形状**。用户可见变化只有两处：AFTER 批次下的催办名单，以及加签批次期间业务摘要里的当前审批人。

## 测试

- `PendingWorklist` 在 `lautung-oa-process/src/test` 建单测：假 `ApprovalEngine` + 假 `PendingSignTaskEvidence` + stub `SysUserService`，钉三源并集、每人每审批一行、AFTER 排除、BEFORE 挂起、姓名顺序与去重、空名/缺用户兜底、`pendingRow` 的优先级与 AFTER 直接 `null`、窗口与全局排序（`createTime desc → 源位次 → id`，今天没有测试钉住）。
- 保留现有 HTTP 合同测试（`ApprovalContractTest`/`ApprovalTransferAddSignContractTest`/`ApprovalActionContractTest`/`BusinessApprovalContractTest`）测接线、权限、文案与响应形状；新增断言：AFTER 批次下催办对象不含原处理人、加签批次期间业务摘要含加签人、待处理第 2 页与排序。
- 成员资格迁移后重跑 `ApprovalMembershipTest` 与 security 套件，权限判定不得有行为变化。

## 考虑过的替代方案

- **只抽一个纯分类器，三处各自取数**：规则一致了，但「三源取数」仍有四份，忘取一源或分页口径漂移的缝原样留着；module 的收益主要来自取数与判定同处。
- **拆成两个 module（待处理工作清单读 + 当前处理人目录）**：两个 module 都要读三源，AFTER/挂起判定要么复制要么互相依赖，等于把这次要消的缝换个位置。
- **module 返回内部行类型、不返回 `ProcessVo`**：行存活过滤与窗口切片会被拆到两个 owner，`total`/AFTER 排除/窗口三件事不再同处；ADR 0045 已定 `ProcessVo` 是唯一行类型。
- **姓名解析继续用调用方注入 `Function`**：每个消费者仍要接一次缝、写一次兜底，正是两份 `displayName` 的由来；process 已依赖 auth，没有架构收益。
- **催办保持 ADR 0068 字面「当前全部处理人」**：会把已记账通过、无可动作任务的 AFTER 原处理人继续列为催办对象；催办的产品含义是提醒该处理的人，待处理列表才是任务通知的定义。
- **membership 吃过滤后的待处理名单**：membership 需要的是原始事实（含 AFTER 原处理人与任何批次），过滤属于消费方；把过滤塞进 module 会让 `canRead`/`canAct` 依赖展示口径。

## 后果

- 三源合成的实现、AFTER/BEFORE 判定与姓名投影各只剩一处；将来加减签、第四种处理人来源只改 `PendingWorklist`（与 membership 的解读），不再三处对齐。
- `LedgerApprovalListReader` 缩成其余四列表 + 委托；`ApprovalWriter.urge` 直接取 `pendingHandlerNames`，不再自己拼引擎任务与加签人。
- 两处用户可见语义变化：AFTER 批次下催办对象少原处理人；加签批次期间业务摘要多出加签人。合同测试按新口径补断言。
- ADR 0045/0068/0043 留修订注记；GLOSSARY「催办」词条改为「提醒待处理名单上的人」。
- 成员资格的实现接入不改变权限结果，但它是安全路径，单独一票并重跑 security 套件。
- 未做：减签、第四种处理人来源、待处理列表的引擎分页改造（仍按窗口底重取，深页整段重取的现状不变）。

Related: #297/#298/#299；2026-11-11 架构审查 C3；ADR 0045（列表读）、0068（审批动作与三源合成）、0043（等待文案）、0011（成员资格）、0019（引擎 port）；GLOSSARY「待处理」「催办」「审批成员资格」。
