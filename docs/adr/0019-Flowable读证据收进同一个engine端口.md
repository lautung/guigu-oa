# Flowable 读证据收进同一个 engine 端口

ADR 0012 只把写入收进 `ApprovalEngine`，并明确把读取留到「第二个 adapter 出现时」。候选 6 指出第二个 adapter 已经存在：成员资格的 `MembershipEvidence` 与 `FlowableMembershipEvidence` 就是同一批引擎读取的另一份实现，`CurrentTask` 因此有两份定义；`OaProcessServiceImpl` 还直接注入 `RuntimeService`、`TaskService`、`HistoryService`，待处理/已处理列表、业务键解析、历史处理人各自绕开端口。现在 `ApprovalEngine` 扩展为读写同一个 port：它继承 `MembershipEvidence`（保留窄接口与 resolver 的依赖），新增 `pendingTasks`、`pendingCount`、`processedTasks`、`processedCount`，并定义 `PendingTask(taskId, processInstanceId, businessKey)` 与 `ProcessedTask(processInstanceId)`。`CurrentTask` 只在 `MembershipEvidence` 定义一次，由 `ApprovalEngine` 继承，`ApprovalWriter` 的 `ApprovalEngine.CurrentTask` 引用不变。

`ProcessEngineApproval` 仍是唯一的 Flowable 调用点：新增 `businessKey(RuntimeService, processInstanceId)` 与 `hasHistoricAssignee(HistoryService, processInstanceId, username)`（沿用原证据实现的空值保护），`FlowableApprovalEngine` 增加 `HistoryService` 依赖后把 `Task` / `HistoricTaskInstance` 映射成端口记录。`FlowableMembershipEvidence` 删除；生产上 `FlowableApprovalEngine` 同时就是成员证据来源，测试里 `@Primary` 的 `TestApprovalEngine` 也仍然只有一个 adapter。`OaProcessServiceImpl` 去掉三个 Flowable 注入，只保留 `ApprovalEngine`：待处理按 businessKey 找账本并跳过空键，已处理按 processInstanceId 找账本，分页计数仍取任务数；wire contract、`show`、`findStarted` 与写入次序都不变。

读取语义保持现状：`hasHistoricAssignee` 查的是历史任务表，包含当前任务（原来的实现也没有 `.finished()`），`ApprovalMembership.isPastApprover()` 继续用「有历史且当前不是处理人」区分；待处理列表跳过缺失实例/空业务键/缺失账本的规则与计数口径不变；业务键仍在适配器里解析，服务层不再持有 `RuntimeService`。测试上 `TestApprovalEngine` 把四个读方法委托给真实适配器；决策记录顺序钩子现在会先遇到成员资格的那次 `currentTasks` 读取，因此钩子在 armed 后跳过第一次读取、对写侧的下一任务读取做断言——这正是「成员判定与写入读同一份证据」的直接结果。`ProcessEngineApprovalTest` 增加 businessKey 与历史处理人（含当前任务、其他用户、空值）用例。

否决的替代方案：删除 `MembershipEvidence`、让 resolver 直接依赖完整 `ApprovalEngine`——resolver 会拿到写入方法，成员单测的假实现也要补全部方法；另建只读端口——seam 又分成两个，候选想消除的两份读取会以另一种形式回来；把 businessKey 的 `RuntimeService` 留在服务层——三个注入只去掉两个。

代价：`ApprovalEngine` 现在同时承担命令与证据两种职责，接口比 ADR 0012 时宽；`ApprovalEngine` 通过父接口继承 `CurrentTask` 的写法依赖 Java 的成员类型继承，读代码时需要知道定义在 `MembershipEvidence`。服务端 145 个测试全绿（原 143 + 2 个 helper 用例）。

Related: ADR 0011（一个 membership gate）；ADR 0012（写入收成一个引擎端口，读取的推迟在此补完）；ADR 0013（配置路由 gate）。
