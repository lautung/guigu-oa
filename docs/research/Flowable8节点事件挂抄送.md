# Flowable 8 节点事件挂抄送

> 研究票：[Flowable 8 不改 BPMN 怎么在节点事件上挂抄送](https://github.com/lautung/guigu-oa/issues/76)
> 母票：[加哪个原创模块来证明从 0 设计](https://github.com/lautung/guigu-oa/issues/66)（已定：抄送规则存业务表，不写进 BPMN）
> 前置：[Flowable 8 如何承接 Activiti 7 存量](../research/Flowable-8如何承接Activiti-7存量.md)（引擎已锁 `org.flowable:flowable-spring-boot-starter-process:8.0.0`）
> 来源：Flowable 开源用户指南；GitHub tag `flowable-8.0.0` / `flowable-7.2.0` / `flowable-7.0.0` 引擎源码；Activiti tag `7.1.0.M6` 源码；本地 `~/.m2` 里 8.0.0 各 jar 的 `javap` 反射签名；本仓库 `后端代码/guigu-oa-parent`。
> 说明：只报官方文档与引擎源码事实。抄送规则表怎么建、抄送列表接口怎么写不在本票范围。

---

## 结论

**推荐：全局 `FlowableEventListener` 只订阅 `TASK_CREATED`，用 `EngineConfigurationConfigurer<SpringProcessEngineConfiguration>` 在引擎构建前注册，抄送写库走引擎所在的同一个 Spring 事务，`isFailOnException()` 返回 `false`，抄送表用 `task_id` 做唯一键去重。**

四条支撑事实：

1. **只有全局事件监听器不碰 BPMN。** `TaskListener` 官方明确「只能作为 `userTask` 的子元素声明」，`ExecutionListener` / `flowable:eventListener` 只能写在 `process` 或活动元素上；`TransactionDependentTaskListener` / `TransactionDependentExecutionListener` 靠 BPMN 属性 `onTransaction` 触发。这些全部要求改 XML，与本项目「zip 上传、发布后 BPMN 冻结」冲突。剩下的合法入口只有 `ProcessEngineConfiguration.eventListeners` / `typedEventListeners`、`RuntimeService.addEventListener`，以及 `CreateUserTaskInterceptor` 这类引擎配置级拦截器。
2. **`TASK_CREATED` 是「审批到达某个人工节点」的唯一贴合事件。** 8.0.0 源码里它在 `UserTaskActivityBehavior.execute` 末尾派发，此时 assignee 已解析、`create` 任务监听器已跑完，事件对象带 `processInstanceId` / `executionId` / `processDefinitionId`，`getEntity()` 是 `TaskEntity`（有 `taskDefinitionKey`、`assignee`）。`ACTIVITY_STARTED` 对**每个**活动（含网关、结束事件、服务任务）都派发且在行为执行**之前**，拿不到任务；`ENTITY_CREATED` 覆盖所有实体类型且早于 assignee 赋值。
3. **立即派发的监听器就在引擎命令的事务里。** `FlowableEventSupport.dispatchEvent` 对 `isFireOnTransactionLifecycleEvent() == false` 的监听器直接同步调 `onEvent`；Spring 下引擎命令默认 `PROPAGATION_REQUIRED`，外层 `@Transactional` 已开事务时直接沿用。所以用 MyBatis-Plus 在 `onEvent` 里插抄送行，与 `ACT_*` 的写入同生共死。代价是：抛异常且 `isFailOnException()` 为 `true` 会把流转一起回滚——官方原话是「若不是业务关键逻辑，建议返回 `false`」。
4. **重复触发是常态，必须做幂等。** 回退/驳回重走同一节点、异步作业重试、多实例节点都会再次派发 `TASK_CREATED`；重新分配走 `TASK_ASSIGNED`、suspend/activate 走 `ENTITY_SUSPENDED` / `ENTITY_ACTIVATED`，都**不**再发 `TASK_CREATED`。因此去重键必须是 `task_id`（而不是 `processInstanceId + 节点 key`），否则第二轮审批的抄送会被误判成重复。

---

## 1. 不改 BPMN 的扩展点清单

| 扩展点 | 类型 / 注册处 | 要不要改 BPMN | 能拿到的上下文 |
|--------|---------------|---------------|----------------|
| `FlowableEventListener`（全局，配置注册） | `AbstractEngineConfiguration.eventListeners` / `typedEventListeners` | **不用** | `FlowableEvent.getType()`；`FlowableEngineEvent` 的 `executionId` / `processInstanceId` / `processDefinitionId` / `scopeType` / `scopeId` / `subScopeId` / `scopeDefinitionId`；`FlowableEntityEvent.getEntity()`；按事件类型另有 `FlowableActivityEvent`（`activityId` / `activityName` / `activityType` / `behaviorClass`）、`FlowableVariableEvent`、`FlowableSequenceFlowTakenEvent` 等 |
| `FlowableEventListener`（全局，运行期注册） | `RuntimeService.addEventListener(listener[, types...])` | **不用** | 同上。官方注明「运行期加的监听器在引擎重启后不保留」 |
| `CreateUserTaskInterceptor` | `ProcessEngineConfigurationImpl.setCreateUserTaskInterceptor` | **不用** | `beforeCreateUserTask(CreateUserTaskBeforeContext)`：`UserTask` 模型、`DelegateExecution`、name/dueDate/priority/formKey/assignee/owner/candidateUsers/candidateGroups 且**可改写**；`afterCreateUserTask(CreateUserTaskAfterContext)`：`UserTask` + `TaskEntity` + `DelegateExecution` |
| 其他配置级拦截器 | `StartProcessInstanceInterceptor`、`EndProcessInstanceInterceptor`、`UserTaskStateInterceptor`、`IdentityLinkInterceptor`、`TaskQueryInterceptor` 等，均为 `ProcessEngineConfigurationImpl` 的 setter | **不用** | 各自的 context 对象；`UserTaskStateInterceptor` 覆盖 claim / unclaim / complete / completeWithForm |
| `ListenerNotificationHelper` 整体替换 | `ProcessEngineConfigurationImpl.setListenerNotificationHelper` | 不用，但要重写引擎内部类 | 全量任务/执行监听器派发链。**不推荐**：属于 `impl` 包，没有兼容承诺 |
| `TaskListener` | `<flowable:taskListener>` | **要**（官方：只能作为 `userTask` 子元素） | `DelegateTask` |
| `ExecutionListener` | `<flowable:executionListener>` | **要**（`process` / 活动 / sequenceFlow 上） | `DelegateExecution` |
| `flowable:eventListener` | `<process><extensionElements>` | **要**（官方：不能定义在单个活动上） | 同全局监听器，但只收该流程定义相关事件 |
| `TransactionDependentTaskListener` / `TransactionDependentExecutionListener` | BPMN 监听器 + `onTransaction` 属性 | **要** | 只有快照：`processInstanceId`、`executionId`、`Task` / `FlowElement` 模型对象、变量 `Map`、customProperties `Map`。**没有** `DelegateTask`、没有当前事务 |
| Flowable → Spring `ApplicationEvent` 桥 | — | — | **8.0.0 不存在**。`flowable-engine`、`flowable-spring`、`flowable-spring-common`、`flowable-spring-boot-autoconfigure` 四个 jar 里没有任何对 `org.springframework.context.ApplicationEventPublisher` 的引用；`flowable-spring` 整个模块只有 13 个类（`SpringProcessEngineConfiguration`、`ProcessEngineFactoryBean`、自动部署策略、测试扩展）。要 Spring 事件得自己在 `onEvent` 里 `publishEvent` |
| 配置属性注册监听器 | `flowable.*` | — | **不存在**。`FlowableProperties` 没有任何 listener / event 字段 |

来源：

- 用户指南 Event handlers（含 `eventListeners` / `typedEventListeners` / 运行期 API / BPMN 级监听器）：<https://www.flowable.com/open-source/docs/bpmn/ch03-Configuration>
- 用户指南 Task listener / Execution listener 的声明位置：<https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs>
- `FlowableEventListener`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common-api/src/main/java/org/flowable/common/engine/api/delegate/event/FlowableEventListener.java>
- `AbstractEngineConfiguration`（`eventListeners` / `typedEventListeners` / `initEventDispatcher`）：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/AbstractEngineConfiguration.java>
- `CreateUserTaskInterceptor` / `CreateUserTaskBeforeContext` / `CreateUserTaskAfterContext`：<https://github.com/flowable/flowable-engine/tree/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/interceptor>
- `ListenerNotificationHelper`（`onTransaction` 分支、快照构造）：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/bpmn/listener/ListenerNotificationHelper.java>
- `onTransaction` 是 BPMN 属性：`BpmnXMLConstants.ATTRIBUTE_LISTENER_ON_TRANSACTION = "onTransaction"`，`flowable-bpmn-extensions.xsd` 枚举 `before-commit` / `committed` / `rolled-back`（`flowable-bpmn-converter-8.0.0.jar`）
- `FlowableProperties`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-spring-boot/flowable-spring-boot-starters/flowable-spring-boot-autoconfigure/src/main/java/org/flowable/spring/boot/FlowableProperties.java>

---

## 2. 该监听哪个事件

### 2.1 `TASK_CREATED` 的派发点与上下文

`UserTaskActivityBehavior.execute` 的顺序（tag `flowable-8.0.0`）：

```text
taskService.createTask() → task.setTaskDefinitionKey(userTask.getId())
  → CreateUserTaskInterceptor.beforeCreateUserTask(...)
  → TaskHelper.insertTask(...)            // 这里派发 ENTITY_CREATED / ENTITY_INITIALIZED
  → handleAssignments(...)                 // assignee / candidateUsers 解析并落库
  → CreateUserTaskInterceptor.afterCreateUserTask(...)
  → executeTaskListeners(task, "create")   // BPMN 里声明的 create 任务监听器
  → dispatchEvent(TASK_CREATED, task)      // ← 全局监听器在这里被调
```

来源：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/bpmn/behavior/UserTaskActivityBehavior.java>

事件对象由 `FlowableTaskEventBuilder.createEntityEvent` 构造成 `FlowableEntityEventImpl`，`populateEventWithCurrentContext` 在 `scopeType == null`（即 BPMN 流程任务）时把 `processInstanceId` / `executionId` / `processDefinitionId` 从 `Task` 复制进事件。

来源：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-task-service/src/main/java/org/flowable/task/service/event/impl/FlowableTaskEventBuilder.java>

因此监听器里能直接拿到：

| 需要的信息 | 取法 |
|------------|------|
| 流程实例 id | `event.getProcessInstanceId()`，或 `task.getProcessInstanceId()` |
| 节点 key | `task.getTaskDefinitionKey()`（`TaskInfo.getTaskDefinitionKey()`） |
| 当前处理人 | `task.getAssignee()`（此刻已解析完） |
| 任务 id（幂等键） | `task.getId()` |
| 流程定义 **key** | `ProcessDefinitionUtil.getProcessDefinition(task.getProcessDefinitionId()).getKey()`（走部署缓存，不额外查库） |
| businessKey（本仓库 = `oa_process.id`） | `CommandContextUtil.getExecutionEntityManager().findById(task.getExecutionId()).getProcessInstanceBusinessKey()`；`AbstractFlowableEngineEventListener.getExecution(event)` 就是这段封装 |
| 流程变量 | 同上拿到 `DelegateExecution` 后 `getVariable(...)`；或订阅 `VARIABLE_CREATED` 的 `FlowableVariableEvent` |

`getExecution(...)` 走的是 `ExecutionEntityManager.findById`，命中的是当前命令的实体缓存，所以**同一事务里刚创建、还没 flush 的流程实例也能读到**——这点对本仓库的「发起」路径是必要的（见 §6.1）。

来源：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/delegate/event/AbstractFlowableEngineEventListener.java>

### 2.2 为什么不用 `ACTIVITY_STARTED`

`ContinueProcessOperation.executeActivityBehavior` 在**调用活动行为之前**派发 `ACTIVITY_STARTED`，对任何 `FlowNode`（用户任务、服务任务、网关、结束事件）都发，多实例节点则改发 `MULTI_INSTANCE_ACTIVITY_STARTED`。事件是 `FlowableActivityEvent`：只有 `activityId` / `activityName` / `activityType` / `behaviorClass`，**没有** task id、没有 assignee。想抄送就得自己判断 `activityType == "userTask"` 再反查任务，而任务此刻还不存在。

来源：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/agenda/ContinueProcessOperation.java>

### 2.3 为什么不用 `ENTITY_CREATED`

官方定义：`ENTITY_CREATED` = 任一实体被创建，涵盖 Attachment / Comment / Deployment / Execution / Group / IdentityLink / Job / Model / ProcessDefinition / ProcessInstance / Task / User。落在 Task 上时它由 `TaskHelper.insertTask` 派发，**早于** `handleAssignments`，此时 `assignee` 仍为 null。要抄送就还得再等一个事件，等价于绕远路。

来源：ch03-Configuration「Supported event types」；`TaskHelper`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/util/TaskHelper.java>

### 2.4 两处文档与 8.0.0 源码不一致（按源码走）

- 文档说 `TASK_CREATED`「若任务属于流程，该事件在任务监听器执行**之前**触发」。8.0.0 的 `UserTaskActivityBehavior` 是**之后**触发（见 §2.1 顺序）。结论：不要依赖 `TASK_CREATED` 与 `create` 任务监听器的相对顺序。
- 文档的事件类型表列了 `UNCAUGHT_BPMN_ERROR`、`MEMBERSHIP_CREATED` / `MEMBERSHIP_DELETED` / `MEMBERSHIPS_DELETED`，而 8.0.0 的 `FlowableEngineEventType` 枚举里**没有**这些；枚举反而多出 `ACTIVITY_CONDITIONAL_WAITING` / `ACTIVITY_ESCALATION_*` / `PROCESS_COMPLETED_WITH_ERROR_END_EVENT` / `PROCESS_COMPLETED_WITH_ESCALATION_END_EVENT` / `CASE_STARTED` / `STAGE_STARTED` / `CHANGE_TENANT_ID` / `BUSINESS_STATUS_UPDATED` 等。以枚举为准。

来源：`FlowableEngineEventType`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common-api/src/main/java/org/flowable/common/engine/api/delegate/event/FlowableEngineEventType.java>

---

## 3. 重复触发矩阵与幂等口径

| 场景 | 会不会再发 `TASK_CREATED` | 依据 |
|------|---------------------------|------|
| 任务被重新分配（`TaskService.setAssignee` / claim / unclaim） | **不会**；发 `TASK_ASSIGNED` | `TaskHelper.fireAssignmentEvents` 只派发 `TASK_ASSIGNED`；`insertTask` 也只在插入时 assignee 非空才补一条 `TASK_ASSIGNED` |
| 流程实例 suspend / activate | **不会**；发 `ENTITY_SUSPENDED` / `ENTITY_ACTIVATED` | 8.0.0 里只有 `SuspensionStateUtil` 引用这两个枚举；官方注明这两个事件只对 ProcessDefinition / ProcessInstance / Task 派发。挂起不重建任务 |
| 异步节点（`flowable:async="true"`）作业失败重试 | **会**。作业每执行一次都重跑 `ContinueProcessOperation`，即重新走 `ACTIVITY_STARTED` → 创建任务 → `TASK_CREATED` | `AsyncContinuationJobHandler.execute` 只做 `planContinueProcessSynchronousOperation(executionEntity)`；失败回滚后作业重试再进同一路径 |
| 回退 / 驳回后重走同一节点（含 `changeState` 类 API） | **会**，而且是合法的第二次抄送 | 每次到达节点都新建 `TaskEntity`，`task.getId()` 不同；`ACTIVITY_STARTED` 另有 `AbstractDynamicStateManager` 这一派发点 |
| 多实例用户任务 | 每个实例一条 `TASK_CREATED` | `ContinueMultiInstanceOperation` / `MULTI_INSTANCE_ACTIVITY_STARTED` 另算 |
| 多节点部署（同库多引擎） | 不会跨节点重复 | 官方：「监听器只收它注册到的那个引擎派发的事件」。反面是**每个进程只看到自己的事件**，抄送逻辑必须在所有跑引擎的实例上都注册 |
| 引擎重启 / 监听器抛异常被吞 | 事件**不会补发** | 事件不落库，`FlowableEventSupport.dispatchNormalEventListener` 在 `isFailOnException() == false` 时只 `LOGGER.warn` |

**幂等口径**：抄送表对 `task_id` 建唯一索引，插入用「存在即跳过」。理由：`task_id` 在异步重试里保持不变（同一次任务创建的重放）而在回退重走时改变（业务上确实要再抄一次）。用 `(processInstanceId, taskDefinitionKey)` 做键会吃掉第二轮的正当抄送；用 `(processInstanceId, taskDefinitionKey, 轮次)` 则需要自己维护轮次，没必要。

一个补充口径：真正会「重复」的只有**跨事务的副作用**。立即派发的监听器如果只用引擎那条连接写库，作业重试时前一次的抄送行已随事务回滚，不会留残行；会留残行的是 `REQUIRES_NEW` 写库、`COMMITTED` 时机写库、以及发消息/推送这类不可回滚动作。

来源：

- `TaskHelper`（`fireAssignmentEvents`、`insertTask`）：见 §2.3 链接
- `AsyncContinuationJobHandler`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/jobexecutor/AsyncContinuationJobHandler.java>
- `FlowableEventSupport`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/event/FlowableEventSupport.java>
- 「Additional remarks」多引擎隔离 + `ENTITY_SUSPENDED` 适用实体：ch03-Configuration

---

## 4. 事务语义

### 4.1 立即派发（推荐）

`FlowableEventSupport.dispatchEvent(event, listener)` 二选一：

```java
if (listener.isFireOnTransactionLifecycleEvent()) {
    dispatchTransactionEventListener(event, listener);   // 注册成 TransactionListener
} else {
    dispatchNormalEventListener(event, listener);        // 直接同步 onEvent
}
```

`dispatchNormalEventListener` 就是在当前线程、当前命令、当前事务里调 `onEvent`；异常按 `isFailOnException()` 决定 rethrow 还是 warn。

Spring 侧：`SpringTransactionInterceptor` 对默认的 `PROPAGATION_REQUIRED`，在「已有活动事务」时**直接透传**给下一个拦截器，不新建 `TransactionTemplate`。所以 `OaProcessServiceImpl.startUp` / `approve` 这类 `@Transactional` 方法调 `runtimeService` / `taskService` 时，引擎命令加入的是同一个 Spring 事务、同一个 Hikari 连接。监听器里用 MyBatis-Plus 写抄送表，因此：

- 与 `ACT_*` 写入**同一事务**，一起提交或一起回滚；
- 能读到同事务内尚未提交的业务行（例如 `startUp` 里刚 `insert` 的 `oa_process`）；
- 抄送写库抛异常时，是否连流转一起回滚，完全取决于 `isFailOnException()`。

来源：

- `FlowableEventSupport`：见 §3 链接
- `SpringTransactionInterceptor`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-spring-common/src/main/java/org/flowable/common/spring/SpringTransactionInterceptor.java>
- 官方对 `isFailOnException` 的原文：「When true is returned, the exception is not ignored and bubbles up, effectively failing the current ongoing command. If the event was part of an API-call (or any other transactional operation, for example, job-execution), the transaction will be rolled back. If the behavior in the event-listener is not business-critical, it's recommended to return false.」（ch03-Configuration）

**坑**：`AbstractFlowableEngineEventListener.isFailOnException()` 硬编码返回 `true`。继承它写抄送、又不重写这个方法，等于「抄送表插失败 → 审批流转回滚 → 用户点通过报错」。要么重写成 `false`，要么直接实现 `FlowableEventListener` / 继承 `AbstractFlowableEventListener`（后者只帮忙实现 `isFireOnTransactionLifecycleEvent()` = `onTransaction != null` 和 `getOnTransaction()`）。

### 4.2 事务生命周期派发（不推荐用于本场景）

监听器让 `isFireOnTransactionLifecycleEvent()` 返回 `true` 并给出 `getOnTransaction()`（取值 `COMMITTING` / `COMMITTED` / `ROLLINGBACK` / `ROLLED_BACK`），`FlowableEventSupport` 就把它包成 `ExecuteEventListenerTransactionListener` 注册到 `TransactionContext`。Spring 下的落点：

| `getOnTransaction()` | `SpringTransactionContext` 注册的回调 |
|----------------------|--------------------------------------|
| `COMMITTING` | `TransactionSynchronization.beforeCommit` |
| `COMMITTED` | `afterCommit` |
| `ROLLINGBACK` | `beforeCompletion` |
| `ROLLED_BACK` | `afterCompletion(status == STATUS_ROLLED_BACK)` |

`COMMITTED` 落在 `afterCommit`：Spring 事务已提交，此时用同一个 `SqlSession` 写库要么被忽略要么报错，必须自己开 `REQUIRES_NEW`——于是抄送与流转分成两个事务，一旦第二个失败就产生「流转成功、抄送丢失」的空洞，还得自己补重试。对「抄送记录是审批的一部分」这个语义来说，代价大于收益。

来源：

- `ExecuteEventListenerTransactionListener`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/event/ExecuteEventListenerTransactionListener.java>
- `SpringTransactionContext`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-spring-common/src/main/java/org/flowable/common/spring/SpringTransactionContext.java>

### 4.3 `TransactionDependentTaskListener` / `TransactionDependentExecutionListener` 的适用场景

两者都是 **BPMN 声明式**的：`ListenerNotificationHelper` 只在 `FlowableListener.getOnTransaction() != null` 时才走这条路，而 `onTransaction` 是 XML 属性。触发时 `ExecuteTaskListenerTransactionListener` / `ExecuteExecutionListenerTransactionListener` 用 `new CommandConfig(false, TransactionPropagation.REQUIRES_NEW)` 起**新事务**执行，回调签名只给快照：

```java
void notify(String processInstanceId, String executionId, Task task,
            Map<String, Object> executionVariables, Map<String, Object> customPropertiesMap);
```

适用场景是「流程事务提交后才该做、且不能影响流转」的外部动作：发邮件、调第三方、写审计到别的库。**不适用**本票：既要改 BPMN，又把抄送推到独立事务。

来源：`ListenerNotificationHelper`（见 §1 链接）；`ExecuteTaskListenerTransactionListener`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/bpmn/listener/ExecuteTaskListenerTransactionListener.java>

### 4.4 官方推荐的做法

用户指南对事件监听器只给了两句可执行的建议：`isFailOnException()` 在非业务关键时返回 `false`；「若可能，用事件暴露的 `EngineServices` 与引擎交互，即使这样，对事件涉及的实体做更新/操作也要谨慎」。没有「抄送这类业务写库该放哪个事务」的专门指引——这属于应用层决策，本票的推荐（同事务 + `failOnException=false` + 唯一键）是在文档给出的两个旋钮上做的选择。

---

## 5. 注册方式（Spring Boot 4.1 + `flowable-spring-boot-starter-process:8.0.0`）

### 5.1 正确姿势

`ProcessEngineServicesAutoConfiguration.StandaloneEngineConfiguration` 继承 `BaseEngineConfigurationWithConfigurers`，在创建 `ProcessEngineFactoryBean` 之前调 `invokeConfigurers(configuration)`；`BaseEngineConfigurationWithConfigurers` 用 `@Autowired(required = false)` 收集容器里所有 `EngineConfigurationConfigurer<SpringProcessEngineConfiguration>`。所以：**定义一个 `ProcessEngineConfigurationConfigurer`（= `EngineConfigurationConfigurer<SpringProcessEngineConfiguration>` 的便捷子接口）Bean，在里面 `setEventListeners(...)`**。引擎 `init` 时 `initEventDispatcher()` → `initEventListeners()` 会把每个监听器交给 `FlowableEventDispatcherImpl.addEventListener(listener)`，而 `FlowableEventSupport.addEventListener` 会读 `listener.getTypes()`：非空就注册成 typed 监听器。也就是说，**重写 `getTypes()` 返回 `Set.of(TASK_CREATED)` 就等价于 `typedEventListeners`，不必用 Map 形式**。

不要用的两条路：直接把 `FlowableEventListener` 声明成 Spring Bean（autoconfigure 完全不引用这个类型，不会被收集）；配置属性（`FlowableProperties` 没有对应键）。

来源：

- `ProcessEngineServicesAutoConfiguration`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-spring-boot/flowable-spring-boot-starters/flowable-spring-boot-autoconfigure/src/main/java/org/flowable/spring/boot/ProcessEngineServicesAutoConfiguration.java>
- `BaseEngineConfigurationWithConfigurers`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-spring-boot/flowable-spring-boot-starters/flowable-spring-boot-autoconfigure/src/main/java/org/flowable/spring/boot/BaseEngineConfigurationWithConfigurers.java>
- `ProcessEngineConfigurationConfigurer` / `EngineConfigurationConfigurer`（`flowable-spring-boot-autoconfigure-8.0.0.jar`）
- `AbstractEngineConfiguration.initEventDispatcher` / `initEventListeners` / `initTypedEventListeners`：见 §1 链接
- `FlowableEventDispatcherImpl`：<https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/event/FlowableEventDispatcherImpl.java>

### 5.2 代码骨架

抄送触发点（值对象，纯数据）：

```java
package com.atguigu.process.cc;

public record CarbonCopyTrigger(
        String processInstanceId,
        String processDefinitionKey,
        String taskDefinitionKey,
        String taskId,
        String assignee,
        String businessKey) {
}
```

抄送写入口（业务侧实现：查规则表 → 插抄送表，`task_id` 唯一键去重）：

```java
package com.atguigu.process.cc;

public interface CarbonCopyWriter {

    void copyOnTaskCreated(CarbonCopyTrigger trigger);
}
```

监听器：

```java
package com.atguigu.process.cc;

import org.flowable.common.engine.api.delegate.event.AbstractFlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.api.Task;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 到达人工节点时按业务规则抄送。规则存业务表，BPMN 不动。
 */
public class CarbonCopyOnTaskCreated extends AbstractFlowableEventListener {

    private final Supplier<CarbonCopyWriter> writer;

    public CarbonCopyOnTaskCreated(Supplier<CarbonCopyWriter> writer) {
        this.writer = writer;
    }

    @Override
    public Collection<? extends FlowableEventType> getTypes() {
        return Set.of(FlowableEngineEventType.TASK_CREATED);
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEngineEntityEvent entityEvent)
                || !(entityEvent.getEntity() instanceof Task task)) {
            return;
        }
        String processDefinitionKey = ProcessDefinitionUtil
                .getProcessDefinition(task.getProcessDefinitionId())
                .getKey();
        DelegateExecution execution = CommandContextUtil
                .getExecutionEntityManager()
                .findById(task.getExecutionId());

        writer.get().copyOnTaskCreated(new CarbonCopyTrigger(
                task.getProcessInstanceId(),
                processDefinitionKey,
                task.getTaskDefinitionKey(),
                task.getId(),
                task.getAssignee(),
                execution == null ? null : execution.getProcessInstanceBusinessKey()));
    }

    /**
     * 抄送写失败不回滚审批流转；失败只留日志，靠唯一键 + 补偿任务收口。
     */
    @Override
    public boolean isFailOnException() {
        return false;
    }
}
```

注册：

```java
package com.atguigu.process.cc;

import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CarbonCopyEngineConfiguration {

    /**
     * writer 用 ObjectProvider 延迟解析：监听器要在 ProcessEngine Bean 之前构造，
     * 而 writer 依赖的业务 Service 往往又（间接）依赖 TaskService / RuntimeService。
     */
    @Bean
    public ProcessEngineConfigurationConfigurer carbonCopyEventListenerConfigurer(
            ObjectProvider<CarbonCopyWriter> writerProvider) {
        return engineConfiguration -> {
            List<FlowableEventListener> listeners = new ArrayList<>();
            if (engineConfiguration.getEventListeners() != null) {
                listeners.addAll(engineConfiguration.getEventListeners());
            }
            listeners.add(new CarbonCopyOnTaskCreated(writerProvider::getObject));
            engineConfiguration.setEventListeners(listeners);
        };
    }
}
```

### 5.3 注册期的两个坑

1. **循环依赖**。configurer 是在创建 `processEngine` Bean 的过程中被调用的。如果 configurer 直接注入 `CarbonCopyWriter`，而 `CarbonCopyWriter` 的实现类注入了 `TaskService` / `RuntimeService`（它们由 `ProcessEngine` 派生），Spring 会报循环依赖。骨架里用 `ObjectProvider` + `Supplier` 把解析推迟到第一次 `onEvent`，绕开这一环。仓库里已有同类处理：`OaProcessServiceImpl` 对 `OaProcessTemplateService` 用了 `@Lazy`。
2. **别覆盖已有监听器**。`setEventListeners` 是整表替换。虽然 8.0.0 的 autoconfigure 目前不往里塞东西，但先读后写（骨架里的 `getEventListeners() != null` 分支）成本为零。

---

## 6. 本仓库落地要注意的三处

### 6.1 「发起」时 `oa_process.process_instance_id` 还没写

`OaProcessServiceImpl.startUp` 的顺序是：`insert(process)` → `startProcessInstanceByKey(key, String.valueOf(process.getId()), vars)` → 之后才 `process.setProcessInstanceId(...)` + `updateById`。第一个人工节点的 `TASK_CREATED` 在 `startProcessInstanceByKey` **内部**派发，那时 `oa_process.process_instance_id` 仍为空。所以监听器**不能**用 `process_instance_id` 反查业务行，要用 businessKey（`String.valueOf(process.getId())`，见 §2.1 取法）。

### 6.2 驳回路径会临时改内存里的 `BpmnModel`

`ProcessEngineApproval.rejectToEnd` 直接改 `repositoryService.getBpmnModel(...)` 返回对象的 outgoing flows，`complete` 后再改回。这段期间到达的是 `EndEvent`，不产生用户任务，因此不会误触发抄送；但如果将来抄送规则改成按 `ACTIVITY_STARTED` 匹配节点，就会看到一条指向 `endEvent` 的异常流转。再一条理由支持锚定 `TASK_CREATED`。

### 6.3 仓库里还留着 Activiti 的 BPMN 内嵌监听器

`service-oa/src/main/resources/process/jiaban02.bpmn20.xml` 用 `activiti:taskListener class="com.atguigu.auth.activiti.MyTaskListener"`，而 `MyTaskListener` 仍 `implements org.activiti.engine.delegate.TaskListener`。Flowable 8 部署时能解析 `activiti:taskListener`（元素按 local name 分派，见前置文档 §5），但类实现的是 Activiti 接口，运行到该节点会失败。这正是「监听器写进 BPMN」的代价：类名冻结在 XML 里，换引擎就得重新发布流程。抄送不要重复这个错。

---

## 7. Flowable 8 vs Flowable 7 vs Activiti 7

对本票涉及的 API 逐文件比对 tag，结论是**没有破坏性变更**：

| 文件 | `flowable-7.0.0` → `flowable-8.0.0` |
|------|-------------------------------------|
| `FlowableEventListener.java` | **完全相同**（含 `getTypes()` default 方法） |
| `FlowableEngineEventType.java` | 只新增枚举常量 `BUSINESS_STATUS_UPDATED`，无改名、无删除（`flowable-7.2.0` → `8.0.0` 同样只差这一条） |
| `FlowableEventSupport.java` | **完全相同** |
| `CreateUserTaskInterceptor.java` | **完全相同** |
| `UserTaskActivityBehavior.java` | `TASK_CREATED` 的派发位置与前后顺序未变。差异集中在 `MigrationContext.getActivityOptions()` 重构、dueDate 支持 `Instant`/`LocalDate`/`LocalDateTime`、候选人抽到 `CandidateUtil`、Jackson 包名 `com.fasterxml.jackson` → `tools.jackson`（Jackson 3） |

包路径也没动：`org.flowable.common.engine.api.delegate.event.*`（`FlowableEventListener`、`FlowableEngineEventType`、`FlowableEngineEvent`、`FlowableEntityEvent`）、`org.flowable.engine.delegate.event.*`（`AbstractFlowableEngineEventListener`、`BaseEntityEventListener`、`FlowableActivityEvent`）、`org.flowable.engine.delegate.TransactionDependent*Listener`、`org.flowable.spring.boot.EngineConfigurationConfigurer`。

相对 **Activiti 7.1.0.M6** 则是整包换名：Activiti 侧对应类在 `org.activiti.engine.delegate.event`（`ActivitiEventListener`、`ActivitiEventType`、`ActivitiEventDispatcher`、`ActivitiEntityEvent`、`ActivitiActivityEvent`、`BaseEntityEventListener` …）。本仓库现在没有任何全局事件监听器代码（只有 §6.3 那个 BPMN 内嵌的 `TaskListener`），所以这次是新写，不涉及移植。

来源：

- 三个 tag 的文件对比经 GitHub Contents API 取原文后 `diff`，路径见 §1–§4 各链接
- Activiti 7.1.0.M6 事件包清单：<https://github.com/Activiti/Activiti/tree/7.1.0.M6/activiti-core/activiti-engine/src/main/java/org/activiti/engine/delegate/event>

---

## 8. 可测性

### 8.1 能测，而且不需要 Spring 上下文

`ProcessEngineApprovalTest` 用的 `ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()` 返回 `StandaloneInMemProcessEngineConfiguration`（继承 `ProcessEngineConfigurationImpl`）。两条注册路径都可用：

- **构建前**：把返回值 cast 成 `ProcessEngineConfigurationImpl`，调 `setEventListeners(List.of(listener))`，再 `buildProcessEngine()`。注意 `ProcessEngineConfiguration` 这个抽象类**没有**暴露 `setEventListeners`（它在 `AbstractEngineConfiguration` 上），所以 cast 是必须的；且 `setHistory(...)` / `setJdbcUrl(...)` 这些 fluent 方法返回 `ProcessEngineConfiguration`，要在链尾 cast。
- **构建后**：`processEngine.getRuntimeService().addEventListener(listener, FlowableEngineEventType.TASK_CREATED)`。走 `AddEventListenerCommand`，官方注明运行期注册的监听器重启不保留——测试里无所谓。

内存引擎同样有 `TransactionContext`：`AbstractEngineConfiguration.initTransactionContextFactory()` 在未注入时装 `StandaloneMybatisTransactionContextFactory`，所以连 `onTransaction=COMMITTED` 这类监听器也能在内存引擎里被断言（只是本票不推荐用）。

### 8.2 测试骨架

```java
package com.atguigu.process;

import com.atguigu.process.cc.CarbonCopyOnTaskCreated;
import com.atguigu.process.cc.CarbonCopyTrigger;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CarbonCopyListenerTest {

    private ProcessEngine processEngine;
    private final List<CarbonCopyTrigger> triggers = new ArrayList<>();

    @BeforeEach
    public void startEngine() {
        ProcessEngineConfigurationImpl configuration =
                (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
                        .createStandaloneInMemProcessEngineConfiguration()
                        .setHistory("full")
                        .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                        .setJdbcUrl("jdbc:h2:mem:cc-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        configuration.setEventListeners(List.of(
                new CarbonCopyOnTaskCreated(() -> triggers::add)));
        processEngine = configuration.buildProcessEngine();
        processEngine.getRepositoryService()
                .createDeployment()
                .addClasspathResource("process/approval-two-step.bpmn20.xml")
                .deploy();
    }

    @AfterEach
    public void stopEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @Test
    public void everyUserTaskArrivalIsSeenOnceWithAssigneeAndNodeKey() {
        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey("approvalTwoStep", "12");

        assertEquals(1, triggers.size());
        assertEquals("first", triggers.get(0).taskDefinitionKey());
        assertEquals("zhangsan", triggers.get(0).assignee());
        assertEquals("approvalTwoStep", triggers.get(0).processDefinitionKey());
        assertEquals(instance.getId(), triggers.get(0).processInstanceId());
        assertEquals("12", triggers.get(0).businessKey());

        Task first = ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "zhangsan", 0, 10).get(0);
        ProcessEngineApproval.complete(processEngine.getTaskService(), first.getId());

        assertEquals(2, triggers.size());
        assertEquals("second", triggers.get(1).taskDefinitionKey());
        assertEquals("lisi", triggers.get(1).assignee());
    }

    @Test
    public void reassignDoesNotTriggerAnotherCopy() {
        processEngine.getRuntimeService().startProcessInstanceByKey("approvalTwoStep", "12");
        Task first = ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "zhangsan", 0, 10).get(0);

        processEngine.getTaskService().setAssignee(first.getId(), "wangwu");

        assertEquals(1, triggers.size());
    }
}
```

`() -> triggers::add` 这一层是把 `Supplier<CarbonCopyWriter>` 的 writer 换成收集器（`CarbonCopyWriter` 只有一个方法，可用 lambda）。断言重点建议锁三条：**节点 key + assignee 拿得到**、**每次到达只发一次**、**重新分配不再发**。驳回重走同一节点会新增一条触发（`taskId` 不同）——这条也值得写成用例，用 `ProcessEngineApproval.rejectToEnd` 拿不到（它走 EndEvent），需要一个带回退连线的测试 BPMN。

来源：`ProcessEngineConfiguration` / `StandaloneInMemProcessEngineConfiguration` / `RuntimeService.addEventListener` 签名取自 `flowable-engine-8.0.0.jar` 的 `javap`；`initTransactionContextFactory` 见 §1 的 `AbstractEngineConfiguration` 链接；「运行期注册不保留」见 ch03-Configuration。

---

## 9. 没查到确切答案的部分

1. **官方没有「业务写库该放哪个事务」的指引。** 用户指南只定义了 `isFailOnException` 与 `isFireOnTransactionLifecycleEvent` 两个旋钮的语义，没有推荐组合。§4 的推荐是本票在这两个旋钮上的判断，不是官方结论。
2. **`TASK_CREATED` 与 `create` 任务监听器的先后，文档与源码相反。** 已按 8.0.0 源码定论（§2.4），但官方没有勘误说明，未来小版本是否会改回文档描述无从判断——所以骨架不依赖这个顺序。
3. **异步作业重试的「同一节点重放次数」没有官方上限说明。** 已确认重试会重跑 `ContinueProcessOperation`（因而重发事件），但 `flowable.*` 里控制重试次数/间隔的具体键位没在本票中核对（本仓库现有 BPMN 都没有 `async="true"` 节点，暂不影响）。
4. **`CreateUserTaskInterceptor` 作为抄送入口没有官方文档**，只有源码与签名。它比事件监听器更早（任务还没插库），能改写 assignee，但拿不到 task id，不适合做幂等键；本票没有进一步验证它在多实例、迁移（`MigrationContext`）路径下的调用次数。
5. **Flowable 8.0.0 的 Release Notes 没有逐条 breaking-change 清单**（<https://github.com/flowable/flowable-engine/releases/tag/flowable-8.0.0>）。§7 的「无破坏性变更」是逐文件 diff 得出的，覆盖范围仅限本票列出的那几个文件。

---

## 来源索引

1. Event handlers / 事件类型表 / `isFailOnException` / 多引擎隔离：<https://www.flowable.com/open-source/docs/bpmn/ch03-Configuration>
2. Task listener 与 Execution listener 的声明位置：<https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs>
3. `FlowableEventListener` / `AbstractFlowableEventListener` / `FlowableEngineEventType` / `FlowableEngineEvent` / `FlowableEntityEvent`（tag `flowable-8.0.0`，模块 `flowable-engine-common-api`）
4. `FlowableEventSupport` / `ExecuteEventListenerTransactionListener` / `FlowableEventDispatcherImpl` / `AbstractEngineConfiguration`（模块 `flowable-engine-common`）
5. `UserTaskActivityBehavior` / `TaskHelper` / `ContinueProcessOperation` / `AsyncContinuationJobHandler` / `ListenerNotificationHelper` / `ExecuteTaskListenerTransactionListener` / `AbstractFlowableEngineEventListener` / `ProcessDefinitionUtil` / `org.flowable.engine.interceptor.*`（模块 `flowable-engine`）
6. `FlowableTaskEventBuilder`（模块 `flowable-task-service`）
7. `SpringTransactionContext` / `SpringTransactionInterceptor`（模块 `flowable-spring-common`）
8. `ProcessEngineAutoConfiguration` / `ProcessEngineServicesAutoConfiguration` / `BaseEngineConfigurationWithConfigurers` / `EngineConfigurationConfigurer` / `ProcessEngineConfigurationConfigurer` / `FlowableProperties`（模块 `flowable-spring-boot-autoconfigure`）
9. `BpmnXMLConstants.ATTRIBUTE_LISTENER_ON_TRANSACTION` 与 `flowable-bpmn-extensions.xsd`（模块 `flowable-bpmn-converter`）
10. 版本比对：tag `flowable-7.0.0` / `flowable-7.2.0` / `flowable-8.0.0` 同名文件 diff；Activiti tag `7.1.0.M6` 的 `org/activiti/engine/delegate/event` 目录
11. 本仓库：`service-oa/src/main/java/com/atguigu/process/**`、`ProcessEngineApproval`、`OaProcessServiceImpl`、`src/test/java/com/atguigu/process/ProcessEngineApprovalTest.java`、`src/test/resources/process/approval-two-step.bpmn20.xml`、`src/main/resources/process/jiaban02.bpmn20.xml`、`application-dev.yml`
