# 审批流转文案对齐 GLOSSARY 通过/拒绝口径且历史行按 description 精确订正

服务端审批文案与 GLOSSARY「处理审批＝通过或拒绝」漂移且**落库**：流转记录写「已通过/驳回」（`ProcessApprovalSupport.recordDescription` → `oa_process_record.description`），实例终态写「审批完成（通过/驳回）」（`applyOutcome` → `oa_process.description`）；客户端全源码零「驳回」，但流转时间线直接渲染落库文案，用户在同屏看到「驳回」与状态标签「审批拒绝」并存（#162 核验时的顺带发现，#172 承接）。#162 已决议：文案收敛为常量、不建字典表、连历史一起迁。

决定：最小换词 + 历史行按 description 精确订正 + status 值域收紧。

- **最小换词**：拒绝侧「驳回」→「已拒绝」、「审批完成（驳回）」→「审批完成（拒绝）」；通过侧「已通过」「审批完成（通过）」本就合规（通过是规范词），不动——迁移范围随之缩到只改「驳回」行，客户端与客户端单测 fixture 零改动。
- **历史订正按 description 精确匹配**，不按 status 映射（V107）：「发起申请」流转记录与审批动作记录共享 `status=1`（`ApprovalWriter` 发起时以 `record(process.getId(), 1, "发起申请", user)` 写入），按「status=1 → 通过侧新文案」映射会把历史发起行批量改写成「已通过」。`oa_process_record` 只改 `description='驳回'`；`oa_process` 只改 `description='审批完成（驳回）' AND status=-1`（status 作辅助确认）。不筛 `is_deleted`、不动时间列（无 `ON UPDATE` 子句，UPDATE 天然不触碰）。status 为空的兜底「驳回」行同被订正——拒绝动作确实发生过，status 为空是另一回事。
- **status 值域收紧为 {1, -1}**：此前 `ApprovalVo.status` 无任何校验，`approve()` 把一切非 1（含缺省/null/0）当拒绝执行——漏传字段即静默拒绝整条流程。`@NotNull(message = "审批状态不能为空")` 管缺省（边界注解，先例 ADR 0033）；值域 {1,-1} 是离散集合、标准注解表达不了（`@Min/@Max` 会放进 0），按「领域/状态判定归 service」落在 `ApprovalWriter.requireStatusInDomain`，非法抛 `GuiguException(201, "审批状态只能为 1（通过）或 -1（拒绝）")`，位于成员资格判定之前（校验先于授权，ADR 0034）。客户端类型钉死 `1 | -1`，收紧对合法调用方零感知。`recordDescription` 随之收敛为对 {1,-1} 的 switch 全覆盖，null 兜底撒谎分支（null → 驳回）删除。model 三处状态注释（`Process`/`ProcessVo`/`ProcessQueryVo` 的 `@Schema`「-1：驳回」）同批对齐为「-1：拒绝」。

## 事实修正（对照 #172 的原始方案）

issue 提的迁移映射是「`oa_process_record`：status=1 → 通过侧新文案，status=-1 → 拒绝侧新文案」，并自知「映射需先定」。核验发现 status 映射无法安全落地：发起记录也是 status=1，会误伤。按 description 精确匹配是唯一既订正目标行又不碰其他行的解；status 只降级为 `oa_process` 侧的辅助 WHERE 条件。

## 考虑过的替代方案

- **按 status 映射**：见上，会把「发起申请」改写成「已通过」，数据损坏。
- **通过侧也换形态**（如统一「审批通过」）：通过侧已合规，动了就要迁「已通过」「审批完成（通过）」两串历史行、改客户端 fixture，收益为零。
- **只换兜底词、不收紧值域**：「非 1 即拒绝」的语义保留，漏字段仍静默拒绝整条流程——文案修好了，危险行为原样留着。
- **值域也用注解（自定义 @Constraint）**：为单字段引入自定义校验注解先例，重于收益；`@Min/@Max` 又放得进 0。service 判定与 ADR 0042 的查重同一家族。

## 后果

- 行为变化：漏 status/非法 status 的审批请求从「静默拒绝流程并返回 200」变为 201 命名拒绝；历史库中「驳回」「审批完成（驳回）」行在下次启动迁移后显示为「已拒绝」「审批完成（拒绝）」。
- 新栈（Flyway 全量重放）与演示种子不受影响：种子不写这些文案，迁移为 no-op。
- 测试同步：`ApprovalContractTest` 拒绝用例期望「已拒绝」；`TestApprovalEngine` 两处证据 SQL 改匹配 `('已通过','已拒绝')`；负例钉在 `RequestValidationContractTest`（缺 status →「审批状态不能为空」、status=0 →「审批状态只能为 1（通过）或 -1（拒绝）」）。
- 未动：`waitingDescription`（等待审批文案）、发起行「发起申请」、客户端全部文案、GLOSSARY（「处理审批」词条本就合规）。

**2026-11-06 修订（#271）**：文案扩到新动作（转办、加签、催办、撤回）与状态档 -2「已撤回」；流转时间线统一读 `oa_process_record` 的动作类型，历史行按 description 精确回填动作类型。见 ADR 0068。

**2026-11-11 修订（C3，ADR 0076）**：等待文案的处理人投影与待处理/催办同源（`PendingWorklist.pendingHandlerNames`），`description` 仍是等待文案唯一 owner，文案格式（`等待X,Y审批`）不变。
