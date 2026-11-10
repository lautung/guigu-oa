# 业务单据生命周期收成一个 module，请假加班只留适配器

ADR 0066/0067 定了业务单据与审批两态、业务键关联、提交即发起与同事务单向回写；ADR 0073 落地请假单与加班单。实现 #282 形成两份近同构的 service（`AttendanceLeaveServiceImpl` 330 行、`AttendanceOvertimeServiceImpl` 275 行）与两份近同构的终态回调（`AttendanceLeaveApprovalCallback` 104 行、`AttendanceOvertimeApprovalCallback` 121 行）：提交顺序、同键重放识别、可提交判断、状态 CAS、申请人回查、摘要文案、终态同值 no-op/异值冲突/CAS 0 行重读各自展开；草稿更新/删除的 CAS 与 `requireCas`/`requireIdempotencyKey`/`applicantOf`/`trimToNull` 等私有辅助成组复制；状态档 0/1/2/-1/-2 在实体、VO 注解、两个回调的 `targetStatus`、DDL 默认值与客户端标签各写一份。ADR 0071 的调岗/离职与 ADR 0072 的报销单按同一形态排队，继续复制会随业务域数量线性增长。另一处事实：`BusinessApprovalStarter.start` 在调用方事务内 catch `DuplicateKeyException` 再调 `startedOutcome` 重读，注释写「这一层没有事务：重读落在新事务里」（`BusinessApprovalStarter.java:47-57`），但两个调用点都在 `@Transactional` 的 submit 里（`AttendanceLeaveServiceImpl.java:142,184`、`AttendanceOvertimeServiceImpl.java:130,167`）；参与者抛异常已把共享事务标 rollback-only，重读也看不见对手提交，ADR 0032 的「冲突重读必须在事务外」在业务发起缝上没有兑现（通用发起的 `OaProcessServiceImpl.startUp` 是在事务外 catch 的）。本 ADR 把业务单据的共用生命周期收成一个 module，并把提交的事务边界与冲突重读收进 module 自己。

## 决定

- 新增业务单据生命周期 module（`attendance.application`）：一个泛型生命周期 + 每单据一个适配器，请假与加班各一个 Spring bean。module 不 import 任何具体单据实体或 VO 业务类型，只认适配器契约里的最小单据视图（标识、申请人、状态）与适配器返回的提交快照。
- module 拥有：草稿编排（加载本人、可编辑判定、CAS 更新/删除的顺序与文案）、提交编排（领域校验 → 定稿快照 → 状态 CAS → 起审批）、同键重放识别、审批摘要绑定、终态回写仲裁（同值 no-op、异值 201、CAS 0 行重读）、提交的事务边界与冲突重读。
- 适配器拥有：持久化与实体映射（加载、插入草稿、CAS 更新/删除、提交快照落库、终态 CAS）、提交前领域校验（请假：日历定稿、区间重叠、余额；加班：同日时刻、入账目标）、终态副作用（请假扣减流水、加班入账流水）、摘要文案与 VO 投影。领域差异不泄漏进 module。
- 状态值域升为 `model` 枚举 `AttendanceApplyStatus`：带 code、`fromCode`、`isEditable`/`isSubmittable`；实体与 DDL 仍存 int，映射只发生在边界。审批结果到单据状态的映射（APPROVED → 2、REJECTED → -1、WITHDRAWN → -2）由生命周期 module 拥有，两个回调里的 `targetStatus` switch 删除。
- 提交的事务边界改为程序化：module 用一个事务包住「状态 CAS + starter 写」；唯一索引冲突时该事务已回滚，随后在事务外重读同一提交标识。只有同一单据的同一提交才返回原审批；其余情况按 201 命名拒绝（提交标识已被使用或单据状态已变化）。`BusinessApprovalStarter` 不再自己 catch 重读，避免在调用方事务内制造 rollback-only。
- 对外保留 `AttendanceLeaveService` / `AttendanceOvertimeService` 两个接口作薄壳：URL、请求/响应形状、controller 与客户端契约不变，实现委托给生命周期。
- 两个 `BusinessApprovalCallback` bean 保留（`businessType()` 注册需要），主体缩成「businessType + 委托 lifecycle 的终态回写」。
- 不改表结构、不新增迁移、不改客户端与服务端响应形状；操作日志与审计口径不变（办事写动作仍不记 OperLog）。

## 测试

- 生命周期 module 在 `lautung-oa-attendance/src/test` 建单测：用假适配器驱动草稿 CAS、提交快照、同键重放、终态同值/异值、事务回滚后的冲突重读、状态迁移表。module 的接口就是测试面。
- 保留现有 HTTP 合同测试（请假/加班/回调/业务审批端口）测接线、权限、文案与响应形状；`AttendanceApprovalCallbackContractTest` 不再重复断言生命周期内部规则，只保留端到端回写。
- 新增用例：同单据同键并发提交仍只有一条审批；拒绝后换新键重提成功；复用旧键被拒绝且单据保持可编辑；冲突重读发生在事务外（可用事务可见性断言）。
- 适配器各自保留小契约测试：请假定稿与余额、加班入账目标缺失回滚。

## 考虑过的替代方案

- **抽象基类 + 两个子类**：继承 `ServiceImpl` 后泛型与事务代理更难测，业务差异仍会以 `protected` 钩子散在继承链上。
- **一个 service + kind 分派**：两个页签的语义挤进一个宽接口，controller 的分支搬进 service；接口变浅，新增单据仍要改分派。
- **等报销/人事落地再收**：届时要改四份而不是两份，新域会先复制再被重构，复制成本已经付出。
- **状态枚举放 process 或让 model 依赖 process**：依赖方向反了；枚举只放 code 与迁移判定，审批结果映射留在 attendance 的生命周期 module。
- **保留 `BusinessApprovalStarter` 的 catch**：事务内重读看不见对手提交，且异常已标 rollback-only，catch 只是把错误推迟到提交时。
- **提交继续整方法 `@Transactional`**：无法在冲突后于事务外重读，ADR 0032 的幂等合同只能靠顺序重放测试勉强覆盖。

## 后果

- 请假/加班两份 service 的重复私有辅助、草稿 CAS、提交序列与两个回调主体消失；新增单据只写适配器与一个注册 bean。
- 新增 `attendance.application` 包；process 的 `BusinessApprovalStarter`/`BusinessApprovalCallback` 端口形状不变，仅移除 starter 内的事务内重读。
- ADR 0032 的幂等合同在通用发起与业务发起两条路径上一致：冲突重读都在事务外；业务发起的「重复送达返回同一条审批」由生命周期 module 自己拥有，不再依赖调用方的事务姿势。
- GLOSSARY 增补「业务单据生命周期」。
- 实现票（C1）阻塞于 C2（ADR 0074）：请假适配器的定稿依赖考勤日历的区间视图。

Related: ADR 0066/0067（两态、业务键与回写）、0073（请假/加班形状）、0032（发起幂等）、0074（考勤日历）；issue #282/#285/#289；C1/C2。
