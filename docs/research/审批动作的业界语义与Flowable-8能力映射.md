# 审批动作的业界语义与 Flowable 8 能力映射

> 研究票：[研究：审批动作的业界语义与 Flowable 8 能力映射](https://github.com/lautung/guigu-oa/issues/269)  
> 地图票：[wayfinder 地图：升级成小团队真跑的企业级 OA（审批深度 + 人事/考勤/报销）](https://github.com/lautung/guigu-oa/issues/267)  
> 决策票：[决策：审批深度第一波——转办/加签/撤回/催办与动态审批人](https://github.com/lautung/guigu-oa/issues/271)  
> 来源：钉钉开放平台 OA 审批文档；飞书开放平台审批 v4 文档与飞书帮助中心；ruoyi-vue-pro（芋道）BPM 文档与 `master` 源码；Flowable 8.0.0 官方用户指南与 `flowable-8.0.0` tag 源码；本仓库 `docs/adr/`、`GLOSSARY.md`、`server/lautung-oa-parent/lautung-oa-process`。  
> 说明：只报语义、API/表/限制与合同冲突面；不做产品选型、不写实现建议。钉钉文档页由前端渲染，本文正文取自其文档站同源 HTML（`icms-document.oss-cn-beijing.aliyuncs.com/zh-CN/dingtalk/orgapp/topics/<slug>.html`），引用仍给官方页面。  
> 口径：「若依工作流」指 ruoyi-vue-pro（芋道）的 `yudao-module-bpm`（Flowable 实现）；若依官方 RuoYi-Vue 本身不含工作流引擎。其他 fork 的语义可能不同。

---

## 结论（先看这 13 条）

1. **转办、委派、委托授权是三个不同动作，不能混用**。转办=永久换当前处理人（`TaskService.setAssignee`）；委派=临时换人、处理完回原处理人（`delegateTask` → `resolveTask`，`owner` + `DelegationState`）；委托授权=一段时间内把审批代理给他人，代理人与原审批人往往同时可处理（飞书明确为「或签关系」）。钉钉/飞书/yudao 对这三个词的使用并不一致。
2. **Flowable 没有「前加签/后加签」原生能力**。公开 API 里没有 append/countersign/sign 一类方法；最接近的是并行多实例的 `RuntimeService.addMultiInstanceExecution` / `deleteMultiInstanceExecution`，而它们只对已经是并行多实例的活动有效。前/后加签必须由业务状态机或自定义任务模型承担；yudao 就是自建子任务实现的。
3. **撤回在 Flowable 里是「结束运行时实例」，不是「撤销一条处理记录」**。直接 API 是 `RuntimeService.deleteProcessInstance(id, reason)`：运行时行删除，`ACT_HI_PROCINST` 以 `STATE_="cancelled"`、`DELETE_REASON_` 结束，历史任务行保留 `DELETE_REASON_`。`changeState` 是移动执行到另一活动，不结束实例；yudao 的取消用 `changeState` 跳到结束节点，并行分支残留时再回退 `deleteProcessInstance`。
4. **催办没有引擎原语**。`TaskService` 没有 remind/urge 方法；钉钉开放平台 OA 审批 API 清单里没有催办端点，只有「OA 限时审批事件」；飞书的催办是产品能力，硬频控是「24 小时内仅可催办 1 次，且此间隔不可更改」；yudao BPM 文档没有催办能力页。
5. **超时在飞书不是自动通过**。飞书只支持超时提醒与超时关闭（审批单失效、可在恢复有效期内恢复数据），FAQ 原文「问：审批流程可以设置一定时间后无人审批则自动批准吗？答：不可以。」；唯一的自动通过与超时无关，是「审批人为空」时的兜底。
6. **会签/或签在 Flowable 里是并行多实例 + 完成条件**：会签 `${nrOfCompletedInstances/nrOfInstances >= 1}`（或 `nrOfCompletedInstances >= nrOfInstances`），或签 `nrOfCompletedInstances == 1`。多实例实例数在进入活动时**只计算一次**；或签一旦满足完成条件，剩余实例会被销毁。
7. **或签/多实例会在引擎历史里留下「没处理过的人」**。被销毁的剩余任务仍有 `ACT_HI_TASKINST` 行（带 `DELETE_REASON_`），而本仓 `hasHistoricAssignee` 查的就是这张表的 `ASSIGNEE_`、且不区分是否完成——这些用户会被算成「历史处理人」，获得 `canRead`。
8. **本仓的「历史处理人」证据在转办/委派后会失效**。`setAssignee`/`delegateTask`/`resolveTask` 都会覆盖 `ACT_HI_TASKINST.ASSIGNEE_` 与 `ACT_HI_ACTINST.ASSIGNEE_`；引擎把 assignee 变更记成**流程级 `participant` 身份链接**（不是任务级 assignee 历史），历史任务/活动只保留最后的值。原处理人不在**当前任务**的 `ACT_HI_TASKINST.ASSIGNEE_` 里，`ApprovalMembership` 的四种关系无法表达「转办走的原处理人」。
9. **ADR 0049 的已处理读是「除发起申请外的一切动作记录」**。`countProcessedRows`/`selectProcessedRows` 只排除 `description = '发起申请'`。一旦新增催办/转办/加签/撤回记录，而操作人不是审批人，这些记录会被算进他的「已处理」。
10. **ADR 0045/0049 的待处理模型只对「线性单任务」成立**。待处理仍由引擎 `taskAssignee` 分页、total=任务数；同一审批出现多个任务（会签/或签/加签）就会多行、total 膨胀。ADR 0049 原文也写明「待处理不动：线性流程同一审批同一时刻只轮到一个节点」。
11. **动态审批人必须写进发布时的 BPMN，不能发布后注入**。模板发布是 zip 原样部署，`deployVerified` 只校验 sha256、指定 key 的定义数量与挂起状态；`oa_process_template` 没有审批人配置字段。已发布模板被 ADR 0003/0046 冻结，既有已发布审批无法获得新审批人规则。
12. **动态审批人的表达式/监听器代码不受发布证据保护**。发布证据只哈希 zip；若 BPMN 里写 `${deptLeaderResolver...}` 或自定义 TaskListener，解析逻辑在服务端代码里，改代码即可改变已发布审批的实际路由，而 zip sha256 不变——这与 ADR 0003「已发布审批步骤保持不变」存在事实张力。
13. **本仓的组织数据不足以直接支撑「部门负责人/岗位」动态审批人**。`sys_dept.leader` 是 `VARCHAR(50)` 姓名，不是用户外键；`sys_post` 被明确为「职务标签，不产生授权、不参与审批分配」；`sys_role` 是后台 RBAC 角色。ADR 0051 还明确写过「审批链继续由 BPMN 指定用户名，本轮不让部门/岗位成为任务分配依据」——地图 #267 已决定重开这条边界。

---

## 0. 先对齐口径

企业审批产品里最容易混的是四个词。本文按下面的区分使用：

| 词 | 本文含义 | 典型实现 |
|---|---|---|
| **转办**（transfer / redirect） | 当前处理人把这条任务**永久**交给另一个人，自己不再处理 | Flowable `TaskService.setAssignee(taskId, newUser)`；钉钉 `POST /v1.0/workflow/tasks/redirect`；飞书 `POST /open-apis/approval/v4/tasks/transfer` |
| **委派**（delegate / 委派） | 当前处理人把任务**临时**交给另一个人，对方处理完回到原处理人 | Flowable `delegateTask` + `resolveTask`，`owner` + `DelegationState`；yudao 的「委派」 |
| **委托授权**（proxy / 委托） | 一段时间内把某类审批**代理**给他人；原审批人通常仍可自行处理 | 飞书「委托授权」：代理期间审批人与代理人为或签关系；钉钉开放平台没有对应 API 文档 |
| **撤回** | 有两种：**发起人撤回**整条审批；**审批人撤回**自己已同意的处理结果 | 发起人撤回=`deleteProcessInstance`/`changeState` 到结束；审批人撤回是产品层状态回滚，Flowable 无原语 |
| **加签** | 在当前节点增加审批人 | 前加签=当前节点之前加节点；后加签=当前节点之后加节点；并加签=当前节点同步加人 |
| **减签** | 移除此前加签出来的人 | Flowable `deleteMultiInstanceExecution`（仅并行多实例）或业务侧删除子任务 |

「会签/或签/依次」是多人审批方式，不是动作：

- **会签**：同一节点所有审批人都同意才通过。
- **或签**：同一节点任一审批人同意即通过，其余任务自动结束。
- **依次审批**：同一节点按顺序逐个审批，全部同意才通过（飞书/yudao 的表述；钉钉 `ONE_BY_ONE`）。

---

## 1. 语义：钉钉、飞书、若依（yudao）怎么定义

### 1.1 一张对照表

| 动作 | 钉钉 OA 审批 | 飞书审批 | yudao BPM（Flowable） |
|---|---|---|---|
| 转办 | 当前审批人把审批单转交给另一人处理；仅支持官方审批/原生流程任务，不支持自有审批创建的实例任务；操作人必须与当前执行人一致 | 「将当前审批转交给其他人审批，自己无需再审批」 | 转办 A→B，B 审批后进入下一节点；后端 `transferTask` 调 `TaskService#setAssignee` |
| 委派 | 开放平台文档没有单列「委派」；有转交 | 产品上叫「委托授权」，是长期代理，不是单任务委派 | A→B，B 审批后转回 A，A 继续审批；`delegateTask` 必须设 owner，B 完成后 `resolveTask` 交回 |
| 前加签 | `type=before`；审批方式 `ALL`/`ONE_BY_ONE`；文档未展开「加签完成后回到谁」 | 「在当前节点之前增加一个审批节点，当新增的节点同意后，再流转至当前节点」 | 在原任务之前插入子任务；所有子任务通过后原任务恢复 |
| 后加签 | `type=after`；文档未写当前节点是否自动通过 | 「在当前节点之后增加一个审批节点，当前节点会默认同意，并流转至新增的节点」 | 原任务先通过（APPROVING），子任务全部通过后原任务才最终完成 |
| 并加签 | 开放 API 只有 before/after，没有并加签参数 | `add_sign_type=3`，与当前审批人共同审批；减签只对并加签开放 | yudao 文档只讲前/后加签与减签；没有独立「并加签」类型 |
| 减签 | 文档未提供减签 API | 仅「并加签」可减签；前/后加签不支持减签 | 删除加签子任务，并修复父任务状态 |
| 发起人撤回 | 只能撤销流程中的实例；发起 15 秒内不能撤销；需模板勾选「允许提交人撤销审批中的审批单」；已完成不能撤销 | 撤回审批中的实例后流程结束；撤回已通过的实例会变回「审批中」；受管理员开关/天数限制 | 发起人侧只能取消自己的运行中流程；可配置是否允许；另有管理员取消端点；子流程一并取消 |
| 审批人撤回 | 无对应 API | 下个节点被处理前，可撤回自己已同意的结果并重新审批；最后一个审批人不支持 | 无对应能力页 |
| 催办 | 开放 API 清单没有催办端点；限时审批事件示例 `actionType=remind` | 提交人点催办提醒审批人；24 小时内仅 1 次，间隔不可改；催办理由仅该审批人可见 | BPM 文档没有催办能力页 |
| 委托/代理 | 开放平台文档没有对应 API | 委托授权：设置代理人、代理时间、代理流程；代理期间审批人与代理人为或签关系，任意一人处理即完成；审批人本人仍可自行处理 | 「委派」是单任务临时委托，不是长期代理 |
| 超时 | 有「OA 限时审批」事件；事件体含 `actionType`（示例 `remind`）、触发时间、提醒对象；开放 API 没有「超时自动通过/拒绝」端点 | 超时提醒 + 超时关闭；不自动通过；FAQ 明确「不可以」；自动通过只用于「审批人为空」兜底 | BPM 文档没有超时自动处理能力页 |
| 会签/或签 | `approvalMethod`：`AND` 会签、`OR` 或签、`ONE_BY_ONE` 依次；`approvalType`：`MANUAL`/`AUTO_AGREE`/`AUTO_REFUSE` | 会签 `AND`、或签 `OR`；帮助中心另有「依次审批」；或签一个任务通过后其余任务自动变已完成 | 并行多实例 + 完成条件；或签 `${nrOfCompletedInstances == 1}`，会签 `${nrOfCompletedInstances >= nrOfInstances}`；依次=顺序多实例 |
| 动态审批人 | 流程预测 API 文档化的选择范围只有 `allStaff`（全公司）/`approvals`（指定成员）/`labels`（角色）；部门主管、发起人上级、岗位不在该枚举里 | 上级、部门负责人、角色、用户组、指定成员、提交人自选、提交人本人、节点审批人、连续多级上级、连续多级部门负责人、表单内联系人、表单内部门 | 角色、部门成员、部门负责人、连续多级部门负责人、岗位、用户、审批人自选、发起人自选、发起人自己、发起人部门负责人、发起人连续多级部门负责人、用户组、表单内用户、表单内部门负责人、流程表达式、审批人为空 |

### 1.2 转办

**钉钉**：`POST /v1.0/workflow/tasks/redirect`，请求体 `taskId`、`toUserId`、`remark`、`operateUserId`、`actionName`、附件。文档原文：

> 「调用本接口，当前审批人可以将审批单转交给另一个人处理。」  
> 「`operateUserId`：操作人的用户ID，需要跟任务的当前执行人保持一致，否则无法通过校验。」  
> 「本接口仅支持通过官方审批发起的或原生审批流程任务，不支持自有审批创建的实例任务。」

来源：[钉钉·转交OA审批任务](https://open.dingtalk.com/document/orgapp/transfer-the-oa-approval-task)

**飞书**：`POST /open-apis/approval/v4/tasks/transfer`，请求体 `approval_code`、`instance_code`、`user_id`（当前审批人）、`transfer_user_id`（被转交人）、`task_id`、可选 `comment`。文档原文：

> 「对于单个审批任务进行转交操作。转交后审批流程流转给被转交人。」

飞书帮助中心对转办的定义：

> 「转交：将当前审批转交给其他人审批，自己无需再审批。例如不方便处理当前审批时，可以转交给其他人。」

来源：[飞书·转交审批任务 API](https://open.feishu.cn/document/server-docs/approval-v4/task/transfer)；[飞书·管理员设置转交、加减签、回退审批](https://www.feishu.cn/hc/zh-CN/articles/360049067381)

**yudao**：

> 「转办：A 转给其 B 审批，B 审批后，进入下一节点。」  
> 「后端由 `transferTask(...)` 调用 `TaskService#setAssignee(...)` 更换审批人。注意：把原审批人设为 owner 只是可选的。」

来源：[yudao·审批转办、委派、抄送](https://doc.iocoder.cn/bpm/task-delegation-and-cc/)

**边界与常见坑**：

- 转办是**永久**换人，不是临时委派。Flowable 的 `setAssignee` 不会设置 `DelegationState`，原处理人之后不会自动拿回任务。
- 三家产品文档都**没有**明确「转办后原处理人是否仍能查看该审批」。飞书只说「自己无需再审批」，没有说是否仍可见；钉钉/飞书 API 也没有返回原处理人可见性。这是一个需要产品裁决的空白。
- 钉钉转交只支持官方审批/原生流程任务，自有审批实例任务不支持；这与其「流程中心」集成模式是两个体系。
- 原处理人如果既不是申请人、也不是当前处理人、也没有抄送，转办后在引擎侧没有任何「原处理人」关系；见 §3.2。

### 1.3 加签（前/后/并）与减签

**钉钉**：`POST /v1.0/workflow/premium/tasks/append`（OA 高级版专享）。文档原文：

> 「调用本接口，当前审批人可对审批任务进行加签操作，加签方式支持前加签、后加签，审批方式支持或签、会签、依次审批等。」

请求体关键字段：

- `type`：`before` 前加签、`after` 后加签。
- `appenderUserIds`：加签人用户 ID 列表。
- `activateType`：`ALL` 或签、`ONE_BY_ONE` 依次审批。
- `agreeAll`：`true` 依次审批/会签，`false` 或签。
- `operateUserId`：必须与任务当前执行人一致，否则校验失败。

来源：[钉钉·加签审批任务](https://open.dingtalk.com/document/orgapp/api-premiumappendtask)

**钉钉文档的两个事实缺口**：

1. 开放 API 只有 `before` / `after`，没有「并加签」参数；产品界面可能有并加签，但开放接口没有。
2. 文档没有写「前加签完成后回到谁」「后加签时当前节点是否自动通过」。`activateType` 与 `agreeAll` 的文案还互相重叠（`ALL` 被写成「或签/或签」，而 `agreeAll=true` 又写成「依次审批/会签」），无法仅凭该页推出完整的多人加签语义。

**飞书**：`POST /open-apis/approval/v4/instances/add_sign`。文档原文：

> 「对于单个审批任务进行加签操作。」

请求体 `add_sign_type`：

> 「1：前加签，在当前操作用户之前审批。  
> 2：后加签，加签后自动通过当前审批，并流转至被加签人。  
> 3：并加签，和当前操作用户共同审批。」

`approval_method`：仅前/后加签需要，`1` 或签、`2` 会签。

飞书帮助中心把「回到谁」写清楚了：

> 「前加签：在当前节点之前增加一个审批节点，当新增的节点同意后，再流转至当前节点。」  
> 「后加签：在当前节点之后增加一个审批节点，当前节点会默认同意，并流转至新增的节点。」  
> 「并加签：在当前节点同步增加其他审批人，审批人是会签还是或签，需遵循当前节点所设置的审批方式。」  
> 「减签：当以『并加签』的方式添加了其他审批人时，才可进行减签操作（前加签、后加签不支持减签）。」

来源：[飞书·审批任务加签 API](https://open.feishu.cn/document/server-docs/approval-v4/task/approval-task-addsign)；[飞书·管理员设置转交、加减签、回退审批](https://www.feishu.cn/hc/zh-CN/articles/360049067381)

**yudao**：

> 「向前加签：在原任务之前插入子任务；在 B、C、D 子任务都审批完成后，任务 A 才能继续审批。」  
> 「向后加签：在原任务之后追加子任务；只有原任务先通过、且所有加签子任务也通过，原任务才最终通过。」  
> 「前加签：清空原任务 `assignee` 并保存到 `owner`，设置 `scopeType=before`，原任务置 `WAITING`；子任务审批通过后由 `handleParentTaskIfSign(...)` 恢复原任务审批人及 `RUNNING`。」  
> 「后加签：子任务先只设 `owner`、状态 `WAITING`；原任务通过后由 `approveAfterSignTask(...)` 将原任务置 `APPROVING`，子任务置 `RUNNING` 并设置审批人。」

来源：[yudao·审批加签、减签](https://doc.iocoder.cn/bpm/sign/)；源码 [`BpmTaskServiceImpl.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java)（`saveWaitTask` 把 `assignee` 置空、`owner` 设为原审批人）。

**边界与常见坑**：

- **前加签会把原任务「挂起」**：原处理人暂时没有 assignee，不能处理；等子任务全部通过后才恢复。任何「当前处理人 = task.assignee」的模型都会在这段时间把原处理人判成「不是处理人」。
- **后加签的「当前节点默认同意」是产品语义，不是 Flowable 语义**。Flowable 没有「先通过但还没完成」的状态；yudao 用 `APPROVING` 自定义状态模拟。
- **多人加签的通过条件**：飞书前/后加签多人时要选会签/或签；钉钉同时有 `activateType` 与 `agreeAll` 两套字段；yudao 的子任务状态机固定「全部通过」。
- **减签只对并加签**（飞书明确；yudao 文档说「加签」但实现有自己的父子任务规则）。前/后加签不能减签。

### 1.4 撤回

#### 1.4.1 发起人撤回

**钉钉**：`POST /v1.0/workflow/processInstances/terminate`。文档原文：

> 「调用本接口，撤销发起的处于流程中的审批实例。」  
> 「审批发起15秒内不能撤销审批流程。」  
> 「本接口只能撤销流程中的审批实例，不能撤销已审批完成的审批实例。」  
> 「调用本接口前，需要在钉钉管理后台 … 勾选允许提交人撤销审批中的审批单，才能撤销审批实例。」

`isSystem`：

> 「`true`：由系统直接终止；`false`：由指定的操作者终止（需要传发起人才能撤销）。」

来源：[钉钉·撤销审批实例](https://open.dingtalk.com/document/orgapp/revoke-an-approval-instance)

**飞书**：`POST /open-apis/approval/v4/instances/cancel`。文档原文：

> 「你可以调用本接口将指定提交人的审批实例撤回。」  
> 「如果撤回的是审批中的实例，则撤回后审批流程结束。」  
> 「若撤回已通过的实例，审批实例会变更为 审批中 的状态。」

前提是管理员勾选「允许撤销审批中的申请」或「允许撤销 x 天内通过的审批」。飞书实例状态包含 `PENDING`（进行中）、`APPROVED`、`REJECTED`、`CANCELED`（已撤回）、`DELETED`（定义停用/删除）。

来源：[飞书·撤回审批实例 API](https://open.feishu.cn/document/server-docs/approval-v4/instance/cancel)；[飞书·审批概述](https://open.feishu.cn/document/server-docs/approval-v4/approval-overview)

**yudao**：页面叫「取消」，不叫「撤回」：

> 「在『我的流程』中可点击某个流程的『取消』按钮，后端调用 `cancelProcessInstance(...)`。」  
> 文档写核心是 `RuntimeService#deleteProcessInstance(...)`；但 `master` 源码的 `moveTaskToEnd` 实际先用 `changeState` 跳到结束节点，若并行分支仍有残留执行，再回退 `deleteProcessInstance`。

源码校验：`cancelProcessInstanceByStartUser` 校验「只能取消自己的」「允许撤销审批中的申请」；另有 `cancelProcessInstanceByAdmin` 管理员取消端点。`moveTaskToEnd` 调 `createChangeActivityStateBuilder().moveActivityIdsToSingleActivityId(...).changeState()`，之后若仍有执行则 `deleteProcessInstance`。

来源：[yudao·流程发起、取消、重新发起](https://doc.iocoder.cn/bpm/process-instance/)；源码 [`BpmProcessInstanceServiceImpl.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java)、[`BpmTaskServiceImpl.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java)

#### 1.4.2 审批人撤回（撤回自己已同意的处理）

**飞书**：产品支持「允许审批人撤回」：

> 「审批人在自己节点已操作『同意』且后序节点还没有被他人审批时，可以撤回『已同意』的审批结果并重新审批。」  
> 「最后一个审批人不支持撤回，即审批结束后不支持审批人撤回。」

来源：[飞书·管理员设置允许审批人撤回](https://www.feishu.cn/hc/zh-CN/articles/491253928695)

**钉钉**：开放平台没有对应的「审批人撤回」API。**yudao**：BPM 文档没有对应能力页。

**边界与常见坑**：

- 「发起人撤回」和「审批人撤回」是两种完全不同的动作：前者结束整条审批，后者只回滚一个处理结果、让审批人重做。
- 钉钉的 15 秒冷却、模板开关、只能撤销运行中；飞书的「允许撤销审批中 / 允许撤销 x 天内通过」、已通过实例撤回后变回审批中；yudao 的「只能取消自己的、运行中、可配置」——三家的窗口与结果都不同。
- 撤回后附件与抄送的处置，三家产品文档都没有完整定义；Flowable 的 `deleteProcessInstance` 也不会碰业务附件表或抄送表。

### 1.5 催办

**飞书**：提交人可催办，硬频控 24 小时 1 次：

> 「提交人可进入审批单据，点击 催办 提醒审批人（24 小时内仅可催办 1 次，且此间隔不可更改）。」  
> 「催办理由仅该审批人可见。」

来源：[飞书·审批常见问题](https://www.feishu.cn/hc/zh-CN/articles/360045410394)

**钉钉**：开放平台 OA 审批 API 清单里没有催办端点；只有「OA 限时审批事件」：

> 「OA限时审批事件，在OA限时审批插件通知相应人员的时候，同时推送给客户业务系统。」

事件体示例里 `actionType` 为 `"remind"`，`isRemind: true`，并带 `userIds`、`triggerType`、`taskContent`。

来源：[钉钉·OA限时审批事件变更](https://open.dingtalk.com/document/orgapp/events-oa-timeout-plugin-task-msg)；[钉钉·OA审批开放能力汇总](https://open.dingtalk.com/document/orgapp/oa-approval-open-capacity-summary)（标准版/高级版能力清单均未列催办 API）

**yudao**：BPM 文档目录没有催办能力页；`bpm/` 下只有转办/委派/抄送、加签/减签、会签/或签/依次、审批人选择、审批结果、流程发起/取消/重新发起。

来源：[yudao BPM 文档索引](https://doc.iocoder.cn/bpm/)

**边界与常见坑**：

- 催办不是任务状态变化，不影响 `ACT_RU_TASK`；它只是一条提醒记录/消息。
- 频率限制与「谁可催办」是产品规则，不是引擎规则。
- 如果用 `ACT_HI_COMMENT` 存催办，`AddCommentCmd` 的 `USER_ID_` 取自 `Authentication.getAuthenticatedUserId()`，本仓从不设置该身份（见 §2.4）；`MESSAGE_` 还会被截断到 163 字符，完整内容进 `FULL_MSG_` LONGBLOB。

### 1.6 委托（代理）与委派

**飞书**：委托授权是长期代理：

> 「审批人在委托授权期间仍可自行审批，此时审批人和代理人为或签关系，即任意一人处理即视为完成。」  
> 「按需设置代理人、代理时间、代理审批流程等内容，点击 发起委托。」

来源：[飞书·审批常见问题](https://www.feishu.cn/hc/zh-CN/articles/360045410394)；[飞书·审批成员手册](https://www.feishu.cn/hc/zh-CN/articles/102254611657)

**yudao**：委派是单任务临时委托：

> 「委派：A 转给其 B 审批，B 审批后，转给 A，A 继续审批后进入下一节点。」  
> 「先调用 `delegateTask(...)`，此时必须将原审批人设为 owner；B 处理完成后，再调用 `resolveTask(...)` 交回给 A。」  
> 「委派只是临时更换审批人，任务完成后要交回原审批人。」

来源：[yudao·审批转办、委派、抄送](https://doc.iocoder.cn/bpm/task-delegation-and-cc/)

**钉钉**：开放平台 OA 审批没有「委托」API 文档；只有转交。

**边界与常见坑**：

- Flowable 的 `delegateTask` 是**单任务**语义：`DelegationState.PENDING`，任务完成后必须 `resolveTask` 回到 owner；它不表达「一段时间内所有新审批都代理」。
- 飞书的委托授权是**长期规则**，代理期间原审批人与代理人或签；这无法用单个任务的 `delegateTask` 表达。
- yudao 的「委派」对应 Flowable 原生 delegation；不要把飞书「委托授权」直接映射成 `delegateTask`。

### 1.7 超时自动处理

**飞书**：只支持超时提醒与超时关闭，不自动通过：

> 「超时设置：选择审批的超时时长。超过该时长后，机器人会给提交人和审批人发送消息提醒，但此时审批单不会关闭、仍然有效。」  
> 「超时关闭：选择审批的关闭时长，即审批在超时多久后关闭，此时审批单便会失效。选择审批的恢复有效期，即审批单在关闭多久之内，允许提交人恢复数据，否则需要重新提交。」

FAQ 原文：

> 「问：审批流程可以设置一定时间后无人审批则自动批准吗？  
> 答：不可以。」

唯一的「自动通过」是审批人为空兜底：

> 「自动通过：审批人为空时自动通过该节点。若该节点还有其他审批人，则由其他审批人继续处理；若无其他审批人，则该节点自动通过。」

来源：[飞书·管理员设置审批超时规则](https://www.feishu.cn/hc/zh-CN/articles/852878870528)；[飞书·审批常见问题](https://www.feishu.cn/hc/zh-CN/articles/360045410394)；[飞书·管理员设置审批人异常流转规则](https://www.feishu.cn/hc/zh-CN/articles/360049067817)

**钉钉**：有「OA 限时审批」事件，但没有「超时自动通过/拒绝」的开放 API；事件示例的 `actionType` 是 `remind`。节点级 `AUTO_AGREE`/`AUTO_REFUSE` 是审批类型（自动通过/自动拒绝），不是超时规则：

> 「`approvalType`：节点审批类型，取值：`MANUAL`：人工审批、`AUTO_AGREE`：自动通过、`AUTO_REFUSE`：自动拒绝。」

来源：[钉钉·获取审批单流程中的节点信息](https://open.dingtalk.com/document/orgapp/approval-process-prediction)；[钉钉·OA限时审批事件变更](https://open.dingtalk.com/document/orgapp/events-oa-timeout-plugin-task-msg)

**yudao**：BPM 文档没有超时自动处理能力页。

**边界与常见坑**：

- 「超时」在产品层通常是提醒/关闭，不是自动审批；不要把节点级自动通过（`AUTO_AGREE`）误当成超时自动通过。
- Flowable 的边界定时器不是「自动完成原任务」，而是中断任务并走另一条顺序流（见 §2.3）。
- 定时器要真正触发需要异步执行器；本仓测试配置 `async-executor-activate: false`（见 §3.9）。

### 1.8 会签、或签、依次审批

**钉钉**：

> 「`approvalMethod`：节点审批方式，取值：`ONE_BY_ONE`：依次审批、`AND`：会签审批、`OR`：或签审批。」  
> 「`actorActivateType`：节点激活类型，取值：`ALL`：并行、`ONE_BY_ONE`：串行。」

来源：[钉钉·获取审批单流程中的节点信息](https://open.dingtalk.com/document/orgapp/approval-process-prediction)

**飞书**：

> 「会签（AND）：表示当前节点需要所有选择的审批人都同意后才被认作同意，进入下一节点。  
> 或签（OR）：表示当前节点只需要有一个审批人同意后就可被认作同意，进入下一节点。」

> 「当其中一个任务通过之后，其他任务会自动变为已完成状态，不再需要其他审批人进行操作。」

帮助中心另有：

> 「依次审批：需要按顺序依次进行审批。」

来源：[飞书·审批概述](https://open.feishu.cn/document/server-docs/approval-v4/approval-overview)；[飞书·管理员设计审批流程](https://www.feishu.cn/hc/zh-CN/articles/360036163653)

**yudao**：

> 「会签：同一个审批节点设置多个人 … 需全部同意之后，审批才可到下一审批节点。Flowable 映射为并行多重事件，完成条件为 `${ nrOfCompletedInstances >= nrOfInstances }`。」  
> 「或签：同一个审批节点设置多个人，任意一个人处理后，就能进入下一个节点。同样使用并行多重事件，但完成条件是 `${ nrOfCompletedInstances == 1 }`。」  
> 「依次审批：多人按顺序依次收到待办 … Flowable 映射为顺序多重事件。」

来源：[yudao·会签、或签、依次审批](https://doc.iocoder.cn/bpm/multi-instance/)

**边界与常见坑**：

- 或签的第一个同意会**销毁**剩余任务；这些用户没有处理过，但引擎会留下带 delete reason 的历史任务行。
- 会签中一人拒绝时，产品通常希望整条审批拒绝；但 Flowable 的多实例完成条件默认只统计「完成数」，拒绝是否算完成、是否终止剩余实例，取决于实现。yudao 的做法是「任务只要审批不通过，整个流程都会被中止（审批不通过），即使在或签场景下」。来源：[yudao·审批通过、不通过、驳回](https://doc.iocoder.cn/bpm/task-todo-done/)。
- yudao 文档对「依次审批」的完成条件前后矛盾：前文说「需全部同意」，后文又说「只要有一个人审批通过，就能进入下一个节点」。这是一手文档自身的不一致。

### 1.9 动态审批人

**飞书**（一手帮助中心，审批节点「设置审批人」）：

> 「上级：可以指定某一层级的上级来进行审批，例如发起人的直属上级。  
> 部门负责人：可以指定某一层级的部门负责人来进行审批，例如发起人的直接部门负责人。  
> 角色：可以指定某个角色例如人事、行政、财务等进行审批。  
> 用户组：可以指定某个特定的用户组成员进行审批。  
> 指定成员：可以指定某位成员来进行审批。  
> 提交人自选：可以由提交人自己来选择审批人。  
> 提交人本人：可以由提交人本人来审批。  
> 节点审批人：可以自动关联前面的节点，由关联节点的实际审批人来再次进行审批。  
> 连续多级上级：可以从提交人的直属上级开始审批，逐级向上审批。  
> 连续多级部门负责人：可以从提交人的直属部门负责人开始审批，逐级向上审批。  
> 表单内联系人：支持关联表单中的联系人字段，并选择联系人本人/联系人上级/联系人部门负责人。  
> 表单内部门：可以关联表单中的部门字段，并指定部门负责人的层级。」

飞书**没有「岗位」这一审批人类型**；角色与用户组来自组织架构。

审批人为空时：

> 「自动通过：审批人为空时自动通过该节点。  
> 指定人员审批：当审批人为空时，该节点将由所指定成员处理。  
> 转交给审批管理员：当审批人为空时，该节点将由所选审批管理员处理。」

审批人与提交人为同一人时：

> 「由提交人对自己审批 / 自动跳过 / 转交给直属上级审批 / 转交给部门负责人审批。」

审批人离职：

> 已设审批交接→按交接规则转交；未设→自动转交直属上级，无上级则流程管理员，仍无则不转交。

来源：[飞书·管理员设计审批流程](https://www.feishu.cn/hc/zh-CN/articles/360036163653)；[飞书·管理员设置审批人异常流转规则](https://www.feishu.cn/hc/zh-CN/articles/360049067817)

**钉钉**：流程预测 API 的 `actorSelectionType` 只文档化三种：

> 「`allStaff`：全公司、`approvals`：指定成员、`labels`：角色。」

`actorSelectionRange` 里 `approvals` 返回 userId/姓名，`labels` 返回角色 id/名称。该页没有列出「部门主管」「发起人上级」「岗位」的枚举值；也没有给出「解析不到人时怎么办」的兜底规则。

来源：[钉钉·获取审批单流程中的节点信息](https://open.dingtalk.com/document/orgapp/approval-process-prediction)

**yudao**：`BpmTaskCandidateStrategyEnum` 的完整策略表（源码）：

| 值 | 含义 |
|---|---|
| 10 | 角色 |
| 20 | 部门的成员（包括负责人） |
| 21 | 部门的负责人 |
| 22 | 岗位 |
| 23 | 连续多级部门的负责人 |
| 30 | 用户 |
| 34 | 审批人自选（当前审批人选择下一节点审批人） |
| 35 | 发起人自选 |
| 36 | 发起人自己 |
| 37 | 发起人部门负责人 |
| 38 | 发起人连续多级部门的负责人 |
| 40 | 用户组 |
| 50 | 表单内用户字段 |
| 51 | 表单内部门负责人 |
| 60 | 流程表达式 |
| 1 | 审批人为空 |

空审批人兜底：

> 「候选人为空时，根据『审批人为空』的配置补充。」  
> `BpmTaskCandidateAssignEmptyStrategy`：指定人员审批 → 返回指定 userId；流程管理员 → 返回流程定义的管理员 userId；都不满足 → 仍返回空集合。

来源：[yudao·选择审批人、发起人自选](https://doc.iocoder.cn/bpm/assignee/)；源码 [`BpmTaskCandidateStrategyEnum.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/enums/BpmTaskCandidateStrategyEnum.java)、[`BpmTaskCandidateInvoker.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/BpmTaskCandidateInvoker.java)、[`BpmTaskCandidateAssignEmptyStrategy.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/strategy/other/BpmTaskCandidateAssignEmptyStrategy.java)

yudao 的任务分配行为（源码）：

> 「第一步，基于分配规则，计算出分配任务的单个候选人。如果找不到，则直接报业务异常，不继续执行后续的流程；第二步，随机选择一个候选人，作为 assignee 负责人。」

但实现里 `calculateTaskCandidateUsers` 在候选集为空时 `return null`，并没有抛业务异常；任务会以无 assignee 落库。注释与实现不一致。多实例任务则从 `collectionElementVariable` 取 assignee，不走随机选择。

来源：[`BpmUserTaskActivityBehavior.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/BpmUserTaskActivityBehavior.java)

**边界与常见坑**：

- **没有统一的「解析顺序」**。三家都是「每个节点选一种审批人规则」，不是「部门负责人 → 角色 → 岗位」的全局优先级链；空结果另有兜底规则。把多个来源排成固定顺序是产品自定义，不是这些产品的既有语义。
- **岗位不是通用审批人类型**。飞书/钉钉文档化类型里没有岗位；只有 yudao 有 `POST` 策略。本仓 `sys_post` 只是职务标签（见 §3.8）。
- **角色语义不同**。飞书角色来自组织架构；钉钉 `labels` 是角色；yudao `ROLE` 是系统角色。本仓 `sys_role` 是后台 RBAC 角色，直接复用会把「授权角色」和「审批角色」混在一起。
- **解析时机**：Flowable 的 `assignee` 表达式/任务监听器在任务创建时求值；多实例的集合在进入活动时只计算一次。审批人配置改了，不会影响已经进入该节点的任务。
- **找不到人**：飞书有三种兜底；钉钉文档未给出；yudao 有「指定人员/流程管理员」兜底，仍空则任务无 assignee。没有 assignee 的任务在本仓待处理列表里不可见（见 §3.4）。

---

## 2. Flowable 8 能力映射

本节以仓库依赖 `org.flowable:flowable-spring-boot-starter-process:8.0.0` 为准（`server/lautung-oa-parent/lautung-oa-process/pom.xml:49-50`）。官方用户指南章节：[BPMN 2.0 Constructs](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs)、[History](https://www.flowable.com/open-source/docs/bpmn/ch10-History)。源码链接统一指向 tag `flowable-8.0.0`。

### 2.1 每个动作对应的 API / 表 / 限制

| 动作 | Flowable API | 主要表/字段 | 限制与事实 |
|---|---|---|---|
| **转办** | `TaskService.setAssignee(taskId, userId)` | `ACT_RU_TASK.ASSIGNEE_`；`ACT_HI_TASKINST.ASSIGNEE_`（会被覆盖）；`ACT_HI_ACTINST.ASSIGNEE_`（会被覆盖）；`ACT_HI_IDENTITYLINK`（流程级 `participant`） | javadoc：「Changes the assignee … No check is done whether the user is known by the identity component.」不校验用户存在；不改变 `OWNER_`/`DELEGATION_` |
| **委派** | `TaskService.delegateTask(taskId, userId)`；`TaskService.resolveTask(taskId)` | `ACT_RU_TASK.OWNER_`、`ASSIGNEE_`、`DELEGATION_`；`ACT_HI_TASKINST.OWNER_`/`ASSIGNEE_`（无 `DELEGATION_` 列） | `delegateTask`：`DELEGATION_=PENDING`，若无 owner 则把原 assignee 设为 owner，再换 assignee；`resolveTask`：`DELEGATION_=RESOLVED`，assignee 换回 owner；**PENDING 状态下 `complete` 会抛 `FlowableException`** |
| **加签/减签** | 无原生「加签」；并行多实例可 `RuntimeService.addMultiInstanceExecution(activityId, parentExecutionId, vars)` / `deleteMultiInstanceExecution(executionId, completed)` | `ACT_RU_EXECUTION`（多实例根/子执行、`nrOfInstances`/`nrOfCompletedInstances`/`loopCounter`）；`ACT_RU_TASK` | `addMultiInstanceExecution` 只对 `isSequential=false` 的多实例活动 `planContinueMultiInstanceOperation`；顺序多实例只创建子执行、不继续。没有「前/后」排序参数。`deleteMultiInstanceExecution` 删除执行/相关数据，delete reason 固定 `"Delete MI execution"` |
| **发起人撤回** | `RuntimeService.deleteProcessInstance(processInstanceId, deleteReason)`；或 `RuntimeService.createChangeActivityStateBuilder()` 移到结束节点 | `ACT_HI_PROCINST.STATE_`、`END_TIME_`、`DELETE_REASON_`；`ACT_HI_TASKINST.END_TIME_`、`DELETE_REASON_`；运行时 `ACT_RU_*` 删除 | `deleteProcessInstance` 不删历史（runtime API 传 `cascade=false`）；completed 实例在运行时表已不存在，会抛 `FlowableObjectNotFoundException`。`changeState` 不结束实例，只移动执行 |
| **催办** | 无 remind/urge API；可用 `TaskService.addComment(taskId, processInstanceId, type, message)`、`setDueDate` | `ACT_HI_COMMENT`（`TYPE_`、`TIME_`、`USER_ID_`、`TASK_ID_`、`PROC_INST_ID_`、`MESSAGE_`、`FULL_MSG_`）；`ACT_RU_TASK.DUE_DATE_`/`ACT_HI_TASKINST.DUE_DATE_` | `addComment` 的 `USER_ID_` 来自 `Authentication.getAuthenticatedUserId()`；`MESSAGE_` 超过 163 字符会被截成 160 + `...`，完整文本进 `FULL_MSG_`；任务已结束则只能传 `processInstanceId` |
| **超时** | 边界定时器 `boundaryEvent` + `timerEventDefinition`（`timeDate`/`timeDuration`/`timeCycle`）；`cancelActivity=true/false` | `ACT_RU_TIMER_JOB`/`ACT_RU_JOB`；`ACT_HI_ACTINST.DELETE_REASON_`；`ACT_HI_TASKINST.DELETE_REASON_` | 定时器只在异步执行器开启时触发；中断式定时器会取消任务并走边界流，不是 `complete` 原任务；非中断式只发提醒、任务保留；多实例上的中断边界事件会销毁所有活动实例 |
| **会签/或签** | 并行多实例 `multiInstanceLoopCharacteristics isSequential="false"` + `completionCondition` | `ACT_RU_EXECUTION` 的多实例变量；每个实例一条 `ACT_RU_TASK`；`ACT_HI_TASKINST` 每个实例一条 | 实例数在进入活动时只算一次；完成条件为真时剩余实例销毁；边界事件/中断会销毁所有实例 |
| **依次审批** | 顺序多实例 `isSequential="true"` | 同上；`loopCounter` 逐实例 | 一次只有一个活动实例（`nrOfActiveInstances` 恒为 1）；`addMultiInstanceExecution` 对顺序多实例不支持 |
| **动态审批人** | `flowable:assignee="${expr}"`、`flowable:candidateUsers`/`candidateGroups`、TaskListener `create` 中 `delegateTask.setAssignee(...)`；多实例 `collection`/`elementVariable` | `ACT_RU_TASK.ASSIGNEE_`；`ACT_RU_IDENTITYLINK`（candidate/assignee）；`ACT_HI_IDENTITYLINK` | `candidateGroups` 的组成员由 Flowable IdentityService 在运行时解析；不校验用户是否存在；候选任务不在 `taskAssignee` 查询里，需要 `taskCandidateUser`/`claim` |

### 2.2 API 原文与关键限制

#### 2.2.1 转办：`setAssignee`

`TaskService#setAssignee` javadoc：

> 「Changes the assignee of the given task to the given userId. No check is done whether the user is known by the identity component.」

源码 `TaskEntityManagerImpl.changeTaskAssignee`：改变 `assignee` 后调用 `recordTaskInfoChange`，再 `update`；`DefaultHistoryManager.recordTaskInfoChange` 会把 `ACT_HI_TASKINST.ASSIGNEE_` 与 `ACT_HI_ACTINST.ASSIGNEE_` 更新为新值。默认 `DefaultIdentityLinkInterceptor.handleAddAssigneeIdentityLinkToTask` 只给**流程实例**加一条 `participant` 身份链接，不是任务级 assignee 历史。

来源：[`TaskService.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/TaskService.java)、[`TaskEntityManagerImpl.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-task-service/src/main/java/org/flowable/task/service/impl/persistence/entity/TaskEntityManagerImpl.java)、[`DefaultHistoryManager.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/history/DefaultHistoryManager.java)、[`DefaultIdentityLinkInterceptor.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/interceptor/DefaultIdentityLinkInterceptor.java)

#### 2.2.2 委派：`delegateTask` / `resolveTask`

`TaskService#delegateTask` javadoc：

> 「Delegates the task to another user. This means that the assignee is set and the delegation state is set to `DelegationState#PENDING`. If no owner is set on the task, the owner is set to the current assignee of the task.」

`resolveTask` javadoc：

> 「Marks that the assignee is done with this task and that it can be send back to the owner. Can only be called when this task is `DelegationState#PENDING` delegation. After this method returns, the `delegationState` is set to `DelegationState#RESOLVED`.」

`DelegationState` 枚举原文：

> `PENDING`：「The owner delegated the task and wants to review the result after the assignee has resolved the task. When the assignee completes the task, the task is marked as `RESOLVED` and sent back to the owner.」
> `RESOLVED`：「The assignee has resolved the task, the assignee was set to the owner again and the owner now finds this task back in the ToDo list for review.」

`CompleteTaskCmd` / `TaskHelper.completeTask` 的实际行为：

> 「A delegated task cannot be completed, but should be resolved instead.」

源码在 `TaskHelper.completeTask` 开头判断 `DelegationState.PENDING` 并抛 `FlowableException`。`DelegateTaskCmd` 设置 `PENDING`、必要时 `owner = 当前 assignee`、再 `changeTaskAssignee`；`ResolveTaskCmd` 设置 `RESOLVED`、`changeTaskAssignee(task, task.getOwner())`。

来源：[`DelegationState.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-task-service-api/src/main/java/org/flowable/task/api/DelegationState.java)、[`DelegateTaskCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/DelegateTaskCmd.java)、[`ResolveTaskCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/ResolveTaskCmd.java)、[`TaskHelper.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/util/TaskHelper.java)

注意：`ACT_HI_TASKINST` 有 `OWNER_`，但**没有 `DELEGATION_`**（见 MySQL history DDL）；任务结束后，`PENDING`/`RESOLVED` 状态只存在于运行时的 `ACT_RU_TASK.DELEGATION_`，历史里只剩 owner。

#### 2.2.3 撤回：`deleteProcessInstance` 与 `changeState`

`RuntimeService#deleteProcessInstance` javadoc：

> 「Delete an existing runtime process instance.」

`DeleteProcessInstanceCmd` 调 `executionEntityManager.deleteProcessInstance(id, reason, false, true)`；`ExecutionEntityManagerImpl.deleteProcessInstanceCascade` 中 `deleteHistory=cascade=false`，最终 `recordProcessInstanceEnd(..., ProcessInstanceState.CANCELLED, deleteReason, ...)`，任务通过 `TaskHelper.deleteTasksByProcessInstanceId(id, reason, false)` 结束并保留历史。因此：

- 运行时 `ACT_RU_EXECUTION`/`ACT_RU_TASK` 删除；
- `ACT_HI_PROCINST` 保留，`END_TIME_`、`STATE_="cancelled"`、`DELETE_REASON_` 写入；
- `ACT_HI_TASKINST` 保留，`END_TIME_`、`DELETE_REASON_` 写入；
- 不会删除 `ACT_HI_*`（除非调用 `HistoryService.deleteHistoricProcessInstance`）。

`RuntimeService#createChangeActivityStateBuilder` 返回 `ChangeActivityStateBuilder`，方法包括 `moveActivityIdTo`、`moveExecutionToActivityId`、`moveActivityIdsToSingleActivityId`、`moveExecutionsToSingleActivityId`、`moveSingleExecutionToActivityIds` 等；它是**移动执行**，不是结束实例。yudao 的 `moveTaskToEnd` 就是先 `moveActivityIdsToSingleActivityId(activityIds, endEventId).changeState()`，并行残留时再 `deleteProcessInstance`。

BPMN 层还有 terminate end event：到达该事件会终止当前 scope；`flowable:terminateAll="true"` 时终止根流程实例，即使事件在嵌套子流程里。来源：[Terminate End Event](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs#terminate-end-event)。这与 `deleteProcessInstance` 的区别是：terminate end event 是一次正常的路由结束（会走后续监听器/历史结束），delete 是 API 级取消（state=cancelled + delete reason）。

来源：[`RuntimeService.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/RuntimeService.java)、[`DeleteProcessInstanceCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/DeleteProcessInstanceCmd.java)、[`ExecutionEntityManagerImpl.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/persistence/entity/ExecutionEntityManagerImpl.java)、[`ChangeActivityStateBuilder.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/runtime/ChangeActivityStateBuilder.java)

#### 2.2.4 催办：`addComment` 与 `setDueDate`

`TaskService#addComment` javadoc：

> 「Add a comment to a task and/or process instance.」

`AddCommentCmd`：

- `taskId` 与 `processInstanceId` 都可以为 null；实现不校验「至少有一个」，两者都空时仍会插入一条无关联的评论（孤儿行）；
- 任务/执行挂起时抛异常；传了 `taskId` 但任务已完成（运行时任务不存在）会抛 `FlowableObjectNotFoundException`，已结束实例只能用 `processInstanceId`；
- `userId = Authentication.getAuthenticatedUserId()`（本仓为 null，见 §2.3）；
- `eventMessage` 超过 163 字符时截成 160 + `...`，写入 `MESSAGE_`；完整消息写入 `FULL_MSG_`。

`setDueDate(taskId, dueDate)` 只改到期时间；查询 API 可以按到期时间过滤/排序，但没有「提醒」动作。

来源：[`TaskService.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/TaskService.java)、[`AddCommentCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/AddCommentCmd.java)

#### 2.2.5 超时：边界定时器

官方用户指南：

> 「A timer boundary event acts as a stopwatch and alarm clock. When an execution arrives at the activity where the boundary event is attached, a timer is started. When the timer fires … the activity is interrupted and the sequence flow going out of the boundary event is followed.」

> 「Non-interrupting means the original activity is not interrupted but stays as it was. The interrupting behavior is the default.」

> 「boundary timer events are only fired when the async executor is enabled (`asyncExecutorActivate` needs to be set to true in the `flowable.cfg.xml`, since the async executor is disabled by default).」

定时器定义三选一：`timeDate`、`timeDuration`（ISO 8601，如 `P10D`）、`timeCycle`（重复间隔，可用于多次提醒/升级）。`timeCycle` 支持 `endDate` 和 cron。

> 「Since a multi-instance is a regular activity, it is possible to define a boundary event on its boundary. In the case of an interrupting boundary event, when the event is caught, all instances that are still active will be destroyed.」

已知问题：

> 「Currently, it is not possible to have multiple outgoing sequence flows attached to a boundary event. A solution to this problem is to use one outgoing sequence flow that goes to a parallel gateway.」

来源：[Flowable 用户指南·Timer Event Definitions](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs#timer-event-definitions)、[Timer Boundary Event](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs#timer-boundary-event)、[Boundary events and multi-instance](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs#boundary-events-and-multi-instance)

**Spring Boot 默认值**：`FlowableProperties` 中 `private boolean asyncExecutorActivate = true;`。所以本仓 dev 不显式设置时异步执行器默认开启；测试配置显式关闭。见 §3.9。

#### 2.2.6 会签/或签/依次：多实例

官方用户指南：

> 「A multi-instance activity is a way of defining repetition for a certain step … sequentially or in parallel.」

> 「The number of instances are calculated once when entering the activity.」

父执行变量：`nrOfInstances`、`nrOfActiveInstances`、`nrOfCompletedInstances`；子执行局部变量 `loopCounter`。

完成条件：

> 「A multi-instance activity ends when all instances are finished. However, it is possible to specify an expression that is evaluated every time an instance ends. When this expression evaluates to true, all remaining instances are destroyed and the multi-instance activity ends.」

示例：`${nrOfCompletedInstances/nrOfInstances >= 0.6 }`。

来源：[Flowable 用户指南·Multi-instance](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs#multi-instance-for-each)

**加签与多实例的接口**：

- `RuntimeService.addMultiInstanceExecution(String activityId, String parentExecutionId, Map<String,Object> executionVariables)`：返回新子执行；`currentNumberOfInstances` +1；只有 `!isSequential()` 时才 `planContinueMultiInstanceOperation`。顺序多实例只创建子执行，不继续。
- `RuntimeService.deleteMultiInstanceExecution(String executionId, boolean executionIsCompleted)`：删除子执行及相关数据；`executionIsCompleted=true` 时 `nrOfCompletedInstances` +1，否则 `nrOfInstances` -1；顺序多实例会继续下一个实例。delete reason 固定 `"Delete MI execution"`。

来源：[`RuntimeService.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/RuntimeService.java)、[`AddMultiInstanceExecutionCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/AddMultiInstanceExecutionCmd.java)、[`DeleteMultiInstanceExecutionCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/DeleteMultiInstanceExecutionCmd.java)

#### 2.2.7 动态审批人：表达式、候选人与监听器

官方用户指南：

- `flowable:assignee="${ldapService.findManagerForEmployee(emp)}"`；返回值必须是 String。
- `flowable:candidateUsers="${ldapService.findAllSales()}"`；返回值 String 或 `Collection<String>`。
- TaskListener `create` 事件里可以 `delegateTask.setAssignee("kermit")`、`addCandidateUser`、`addCandidateGroup`。
- `candidateGroups` 的组成员在运行时通过 Flowable IdentityService 解析。
- 「no check is made whether a provided user is known by the identity component」——引擎不校验用户存在。

来源：[Flowable 用户指南·User assignment](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs#user-assignment)、[Custom Assignment via task listeners](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs#custom-assignment-via-task-listeners)

### 2.3 历史与身份链路

`history-level: full`（`application-dev.yml:78`）下，6 类历史实体都会写（官方 History 章节）：HistoricProcessInstances、HistoricVariableInstances、HistoricActivityInstances、HistoricTaskInstances、HistoricIdentityLinks、HistoricDetails。

关键表与字段（Flowable 8.0.0 MySQL DDL）：

| 表 | 关键字段 | 与本主题相关的事实 |
|---|---|---|
| `ACT_HI_PROCINST` | `ID_`、`PROC_INST_ID_`、`BUSINESS_KEY_`、`START_USER_ID_`、`START_TIME_`、`END_TIME_`、`STATE_`、`DELETE_REASON_` | `START_USER_ID_` 来自 `Authentication.getAuthenticatedUserId()`（本仓为空，见 §2.3）；`deleteProcessInstance` 写 `STATE_="cancelled"` 与 delete reason |
| `ACT_HI_TASKINST` | `ASSIGNEE_`、`OWNER_`、`START_TIME_`、`END_TIME_`、`DURATION_`、`DELETE_REASON_`、`COMPLETED_BY_`、`TASK_DEF_KEY_` | `ASSIGNEE_` 会被 `setAssignee`/`delegate`/`resolve` 覆盖；没有 `DELEGATION_` 列；`COMPLETED_BY_` 只在显式传 userId 时才有值（本仓 `complete(taskId)` 不传，见下）；任务结束（含被删除）时 `STATE_` 置为 `COMPLETED`，`DELETE_REASON_` 区分正常完成与删除 |
| `ACT_HI_ACTINST` | `ACT_ID_`、`ACT_TYPE_`、`ASSIGNEE_`、`COMPLETED_BY_`、`END_TIME_`、`DELETE_REASON_` | `ASSIGNEE_` 同样会被覆盖；`COMPLETED_BY_` 由 `recordActivityTaskEnd(..., userId, ...)` 写入 |
| `ACT_HI_IDENTITYLINK` | `USER_ID_`、`GROUP_ID_`、`TYPE_`、`TASK_ID_`、`PROC_INST_ID_` | 默认 assignee 变更只加**流程级** `participant` 链接（`TASK_ID_` 为空）；不是任务级 assignee 历史 |
| `ACT_HI_COMMENT` | `TYPE_`、`TIME_`、`USER_ID_`、`TASK_ID_`、`PROC_INST_ID_`、`MESSAGE_`、`FULL_MSG_` | 评论/催办若走这里，作者依赖 `Authentication`；`MESSAGE_` 截断 163 |
| `ACT_RU_TASK` | `ASSIGNEE_`、`OWNER_`、`DELEGATION_`、`DUE_DATE_`、`CREATE_TIME_` | 运行时当前处理人/委派状态/到期时间 |
| `ACT_RU_IDENTITYLINK` | `TYPE_`、`USER_ID_`、`GROUP_ID_`、`TASK_ID_`、`PROC_INST_ID_` | candidate/assignee/owner/participant 链接 |

来源：[Flowable 8.0.0 MySQL history DDL](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/resources/org/flowable/db/create/flowable.mysql.create.history.sql)、[engine DDL](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/resources/org/flowable/db/create/flowable.mysql.create.engine.sql)、[common DDL](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/resources/org/flowable/common/db/create/flowable.mysql.create.common.sql)、[`ActivityInstanceEntityManagerImpl.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/persistence/entity/ActivityInstanceEntityManagerImpl.java)

**本仓的身份链路事实**：

- 仓库没有任何 `Authentication.setAuthenticatedUserId` 调用（全仓 grep 无结果）。`start` 只传 `processDefinitionId`、`businessKey`、variables（`ProcessEngineApproval.java:86-92`）。
- Flowable 的 Spring Security 桥 `FlowableSecurityAutoConfiguration` 需要 `IdmIdentityService` bean；IDM 自动配置 `IdmEngineServicesAutoConfiguration` 又要求 `org.flowable.idm.spring.SpringIdmEngineConfiguration` 在 classpath（`@ConditionalOnIdmEngine` 的 `@ConditionalOnClass`）。本仓依赖树有 `flowable-idm-engine`/`flowable-idm-api`/`flowable-idm-engine-configurator`，但没有 `flowable-idm-spring`，因此 IDM 服务 bean 与 Spring Security 桥都不生效。结果是 `Authentication.getAuthenticatedUserId()` 为 null（除非调用线程手工设置），HTTP 登录用户不会自动进入引擎历史。来源：[`FlowableSecurityAutoConfiguration.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-spring-boot/flowable-spring-boot-starters/flowable-spring-boot-autoconfigure/src/main/java/org/flowable/spring/boot/FlowableSecurityAutoConfiguration.java)、[`IdmEngineServicesAutoConfiguration.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-spring-boot/flowable-spring-boot-starters/flowable-spring-boot-autoconfigure/src/main/java/org/flowable/spring/boot/idm/IdmEngineServicesAutoConfiguration.java)、[`ConditionalOnIdmEngine.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-spring-boot/flowable-spring-boot-starters/flowable-spring-boot-autoconfigure/src/main/java/org/flowable/spring/boot/condition/ConditionalOnIdmEngine.java)。
- 因此 `ACT_HI_PROCINST.START_USER_ID_` 与 `ACT_HI_COMMENT.USER_ID_` 在本仓路径上为空；申请人权威仍是 `oa_process.user_id`。`ACT_HI_TASKINST.COMPLETED_BY_` 也为空，因为 `complete(taskId)` 不带 userId（`ProcessEngineApproval.java:94-96`）；但 `ACT_HI_ACTINST.COMPLETED_BY_` 在 `recordActivityTaskEnd` 里会在 completerUserId 为空时回退成 `task.getAssignee()`，所以活动历史仍能看到「谁被分配/完成」。操作人权威仍在 `oa_process_record.operate_user_id`。若将来通过 Spring Security 桥或 `setAuthenticatedUserId` 打开身份投射，`START_USER_ID_` 会是 **username 字符串**，不是 `sys_user.id`，与 `oa_process.user_id` 是两套身份维度。
- `hasHistoricAssignee` 查 `ACT_HI_TASKINST.taskAssignee(username).count() > 0`（`ProcessEngineApproval.java:75-84`），没有 `.finished()`，也不区分「完成」「删除」「转办覆盖」。它既包含当前任务，也包含被或签销毁的任务。

### 2.4 多实例/定时器对任务模型的影响

- 会签/或签会在同一 `processInstanceId` 下产生多条 `ACT_RU_TASK`；`currentTasks(processInstanceId)` 返回全部（`ProcessEngineApproval.java:98-103`），`ApprovalMembership.currentTaskIds` 会包含该用户的所有当前任务。
- 或签完成条件触发后，剩余任务被销毁；`ACT_HI_TASKINST` 仍有行，`DELETE_REASON_` 有值，`ASSIGNEE_` 仍是原审批人。
- 中断式边界定时器挂多实例时，会销毁所有活动实例；非中断式只触发提醒路径，任务保留。
- 定时器 job 在 `ACT_RU_TIMER_JOB`/`ACT_RU_JOB`；异步执行器关闭时不会触发。测试配置 `async-executor-activate: false`（`application-test.yml:28`），dev 未显式设置（`application-dev.yml:75-79`），Spring Boot 属性默认 `true`（`FlowableProperties.java:41`）。

---

## 3. 与本仓现有合同的冲突面

本节只列事实与冲突点；取舍留给 #271。

先列本仓相关合同：

- **ADR 0011**：审批成员资格收成一个 gate；`ApprovalMembership` 的四种关系=申请人、当前处理人、历史处理人、抄送人；`canRead`/`canUpload`/`canAct` 从同一份证据派生；`isApprove` 是实例级，`canAct` 是任务级；**并行/多实例任务暂不支持**。
- **ADR 0049**：已处理行单位=审批；读证据从引擎历史换成 `oa_process_record` 动作记录；`countProcessedRows`/`selectProcessedRows` 按 `operate_user_id` 去重，排除 `description='发起申请'`；待处理仍走引擎，前提是「线性流程同一审批同一时刻只轮到一个节点」。
- **ADR 0045**：五种审批列表读收成一个 `ApprovalListReader`；行形状唯一 owner 是 `OaProcessMapper.xml`；待处理由引擎分页、total=任务数；不把 `ACT_*` join 进账本 SQL。
- **ADR 0046 / 0003**：发布证据=zip 产物路径+sha256+流程定义 key/id/部署 id；zip 原样部署；已发布模板冻结，恢复不得猜测。
- **ADR 0004**：处理结果待确认；离开不撤销已提交处理；客户端恢复记录只归原操作。
- **GLOSSARY**：审批、待处理、处理审批、已处理、附件、审批成员资格的定义；「处理审批」只指通过/拒绝，「一次处理不代表整条审批已经结束」。

### 3.1 冲突矩阵

| 动作 | 主要触发的合同 | 冲突点（事实） |
|---|---|---|
| **转办** | ADR 0011、ADR 0049、GLOSSARY「已处理」 | 原处理人失去 `currentTaskIds`；当前任务的 `ACT_HI_TASKINST.ASSIGNEE_` 被覆盖为新人，若他此前没有其他已完成任务，`hasHistoricAssignee` 也为 false → `canRead=false`（403）；转办本身不写 `oa_process_record`，不进任何人的「已处理」；若补写记录，又会被 ADR 0049 的谓词算成「处理」 |
| **前加签** | ADR 0011、ADR 0045/0049、ADR 0004 | 原任务被挂起（`assignee` 置空/`owner` 保存）→ 原处理人不在 `currentTaskIds`，不能 `canAct`、可能不能 `canRead`；原任务仍可被 `currentTasks` 读到但 assignee 为空；新子任务让同一审批在待处理出现多行；`ApprovalWriter.approve` 的 `applyOutcome` 可能提前把审批标成完成 |
| **后加签** | 同上 + ADR 0003/0046 | 当前节点「默认同意」不是 Flowable 状态；需要自定义 `APPROVING` 状态；如果 BPMN 没有对应结构，只能自建任务/状态机；发布证据不覆盖自定义任务 |
| **并加签/减签** | ADR 0011、ADR 0045/0049 | 并行任务让 `isApprove` 实例级布尔与单 `taskId` 路由不匹配；`currentTasks` 多任务；`addMultiInstanceExecution` 只对并行多实例活动有效；减签删除执行的历史/处理人证据不完整 |
| **发起人撤回** | ADR 0004、ADR 0049、状态模型 | 需要新 `oa_process.status`（现有 0/1/2/-1）与客户端状态映射；撤回后已处理行仍存在（记录未删）；如果写撤回记录，会进操作人的「已处理」；`deleteProcessInstance` 不更新账本，`oa_process` 仍显示审批中 |
| **催办** | ADR 0049、ADR 0045、ADR 0004 | 无引擎 API；若写 `oa_process_record`，会被「除发起申请外都算处理」的谓词算进已处理；若写 `ACT_HI_COMMENT`，作者为 null（本仓不设 Authentication）；频率控制需要按人/按审批/时间查询，现有表无类型列 |
| **委托/委派** | ADR 0011、ADR 0049、ADR 0004 | `delegateTask` 的 owner 不进入 `ApprovalMembership`；`complete` 在 PENDING 会抛异常；`resolveTask` 不写 ledger；`ACT_HI_TASKINST.ASSIGNEE_` 覆盖导致原/代理人历史丢失；飞书式「或签代理」无法用单任务 delegation 表达 |
| **超时自动处理** | ADR 0003/0046、ADR 0004、状态模型 | 定时器行为必须写进发布时的 BPMN；已发布模板不能新增；定时器中断任务不走 `ApprovalWriter`，账本/状态/记录不会更新；没有操作人，`oa_process_record.operate_user_id` 为空；自动通过/拒绝与现有 status 语义需要区分 |
| **会签/或签** | ADR 0011、ADR 0045/0049 | 多任务违反 ADR 0011「顺序单任务」前提；待处理多行/total=任务数；或签销毁的任务让未处理人变成「历史处理人」；`rejectToEnd` 只改一个任务节点的 outgoing，不保证终止整条多实例 |
| **动态审批人** | ADR 0003/0046、ADR 0051、ADR 0011、ADR 0045 | 必须写进冻结的 zip；`oa_process_template` 无审批人配置；表达式/监听器代码不在 sha256 证据内；`sys_dept.leader` 是姓名不是用户引用；`sys_post` 不参与审批分配；`candidateGroups` 走 Flowable IDM（本仓不维护）；`candidateUsers` 任务不在 `taskAssignee` 查询里 |

### 3.2 成员资格 gate（ADR 0011）

本仓实现：

- `ApprovalMembership` 只有四个关系：`applicant`、`currentTaskIds`、`historicApprover`、`ccMember`（`membership/ApprovalMembership.java:11-15`）。
- `canRead = applicant || isCurrentApprover() || isPastApprover() || ccMember`（`:41-43`）。
- `canUpload = applicant || isCurrentApprover()`（`:45-47`）。
- `canAct(taskId) = currentTaskIds.contains(taskId)`（`:49-51`）。
- `isPastApprover = historicApprover && !isCurrentApprover()`（`:33-35`）。
- `ApprovalMembershipResolver` 只按 `username.equals(task.assignee())` 收集当前任务 id（`membership/ApprovalMembershipResolver.java:35-46`）；历史处理人走 `hasHistoricAssignee`（`:49-50`）；抄送走 `ProcessMembershipExtension`（`:51-53`）。
- `show` 的 `isApprove` 是 `membership.isCurrentApprover()`（`service/impl/OaProcessServiceImpl.java:67-68`），即实例级布尔；客户端详情只从路由拿一个 `taskId`（`client/guigu-oa-client/src/views/work/pending/detail.vue:201`）。

逐动作冲突：

**转办**

- `setAssignee` 后，原处理人的 `currentTaskIds` 为空；`hasHistoricAssignee` 查 `ACT_HI_TASKINST.ASSIGNEE_`，该列已被改成新处理人 → 当前任务的 `historicApprover=false`。若原处理人此前没有其他已完成任务（例如单节点、尚未处理），他既不是申请人/抄送人、也不是历史处理人，`canRead=false`，`show`/附件直接 403；若他处理过更早的节点，那些已完成任务的 assignee 不会被本次转办覆盖，仍可作为历史处理人读到详情。
- `ApprovalMembership` 没有「转办人/原处理人」关系，`ProcessMembershipExtension` 目前只服务抄送；要保留原处理人可见性，现有的四种关系不够。
- `show` 的 `isApprove` 在新处理人侧为 true；原处理人侧为 false。附件补传 `canUpload` 同步失效。

**前加签**

- 原任务被挂起（yudao 的 `saveWaitTask` 把 `assignee` 置空、`owner` 设为原审批人；`BpmTaskServiceImpl.java` 源码）。`currentTasks` 返回该任务但 `assignee=null`，resolver 不会把它放进 `currentTaskIds` → 原处理人 `canAct=false`，且 `isCurrentApprover=false`；`hasHistoricAssignee` 若 assignee 已被置空，也可能 false → 原处理人可能直接失去 `canRead`。
- 原任务仍是当前任务，`isApprove` 为 false；但用户点开详情时可能仍看到审批按钮（取决于前端），提交时服务端 403。
- 子任务的 assignee 是加签人，`canAct` 正常；但「前加签全部通过后恢复原任务」需要业务侧把 `assignee` 写回，`ApprovalMembership` 没有「等待恢复」状态。

**后加签**

- 当前任务在「默认同意但未完成」期间，`assignee`/`owner`/状态由业务侧维护；`ApprovalMembership` 只认 `task.assignee`。如果当前任务 assignee 被清空或改成子任务人，原处理人失去 `canAct`/`canRead`。
- `isApprove` 是实例级，子任务运行期间原处理人/加签人可能都看到 `isApprove=true`，但只有对应 `taskId` 能 `canAct`。

**并加签/会签/或签**

- `currentTasks` 返回多条任务；`currentTaskIds` 是该用户的所有当前任务 id；`canAct(taskId)` 仍逐任务正确。
- `show` 的 `isApprove` 是「任一当前任务分配给我」，不是「路由里的 taskId 分配给我」。多任务时，布尔为 true 但路由 `taskId` 可能不是该用户的 → 客户端显示可审批、服务端 403。
- 或签完成条件销毁剩余任务后，被销毁用户的 `ACT_HI_TASKINST.ASSIGNEE_` 仍在，`hasHistoricAssignee` 为 true → `isPastApprover=true` → `canRead=true`，尽管他从未处理过。这直接改变 ADR 0011「历史处理人」的语义。

**发起人撤回**

- `deleteProcessInstance` 后 `currentTasks` 为空；申请人 `canRead=true`；历史处理人的 `ACT_HI_TASKINST` 保留 → `hasHistoricAssignee=true` → `canRead=true`；抄送仍可读。成员资格本身不冲突。
- 但 `show` 的 `isApprove=false`、`canAct=false`；如果客户端仍显示「审批中」，需要状态/成员资格联动。

**催办**

- 不改成员资格；催办人不应因催办获得 `canRead`/`canAct`。如果催办记录被当成「历史处理人」证据，就会错误扩权（当前不会，因为 `hasHistoricAssignee` 只查引擎历史）。

**委托/委派**

- `delegateTask` 把 `assignee` 换成代理人，`OWNER_` 设为原审批人；`currentTasks` 只返回 assignee → 代理人 `canAct=true`，原审批人 `canAct=false`、`currentTaskIds` 为空；原审批人的 `hasHistoricAssignee` 被覆盖为代理人 → 原审批人可能连 `canRead` 都没有。
- `ApprovalMembership` 没有 `owner` 字段；无法表达「原审批人是 owner、代理人当前处理」。飞书式「审批人与代理人或签」更无法表达（Flowable delegation 同一时刻只有一个 assignee）。
- `resolveTask` 把 assignee 换回 owner，但 `DELEGATION_` 状态只在运行时；`ACT_HI_TASKINST` 没有 delegation 列，历史只剩最终 assignee。

**动态审批人**

- 如果解析成 `assignee`：成员资格按 assignee 工作，无新增关系；但「为什么是他」没有证据（除非写 ledger/变量）。
- 如果解析成 `candidateUsers`/`candidateGroups`：任务 `ASSIGNEE_` 为空，resolver 的 `username.equals(task.assignee())` 永远 false → 候选人 `canAct=false`、`canRead` 可能 false；`pendingTasks` 只查 `taskAssignee`，候选任务根本不出现。要支持候选任务，ADR 0011/0045 需要新增「候选人」关系与认领动作。

### 3.3 已处理行单位=审批（ADR 0049）

本仓 SQL（`mapper/xml/OaProcessMapper.xml:108-138`）：

- `countProcessedRows`：`count(distinct r.process_id) ... where r.operate_user_id = #{userId} and r.is_deleted = 0 and a.is_deleted = 0 and r.description != #{excludeDescription}`。
- `selectProcessedRows`：派生表按 `r.operate_user_id` 过滤、按 `process_id` 分组取 `max(create_time)`，排除 `description='发起申请'`，再 join `oa_process` 取行。
- `excludeDescription` 来自 `ProcessApprovalSupport.INITIATION_DESCRIPTION`（`ProcessApprovalSupport.java:24`）。

冲突：

- **任何新增的非「发起申请」动作记录都会被算进该操作人的「已处理」**。这包括：申请人催办、申请人撤回、当前审批人转办、当前审批人加签/减签、审批人撤回自己的处理。它们不是 GLOSSARY 的「处理审批」（通过/拒绝），但 SQL 只看 `operate_user_id` + `description != 发起申请`。
- 如果新增动作不写记录，则动作不可追溯；如果写记录，就必须同时改 processed 谓词（例如加记录类型白名单/类型列），`countProcessedRows` 与 `selectProcessedRows` 要一起改。
- `ProcessApprovalSupport.recordDescription(Integer status)` 只接受 1/-1（`:26-32`）；新动作没有对应的 status 值，不能复用这个方法。
- 撤回后已处理行：`oa_process_record` 不删除，processed SQL 也不看 `oa_process.status`，所以撤回不会让已处理行消失；同一审批的 total 不变。如果产品要求「撤回后不出现在已处理」，需要额外过滤。
- 或签被销毁任务的人：没有 `oa_process_record`，不会进已处理；但可能因 `hasHistoricAssignee` 进「历史处理人」（见 §3.2），出现「已处理列表没有、详情却能看」的不一致。
- 超时自动动作：没有操作人，`operate_user_id` 为空；processed SQL 按用户 id 过滤，所以不会进任何人的已处理。如果产品要求「系统自动处理」出现在某人的已处理/审批记录里，需要新的 actor 类型。

### 3.4 待处理读（ADR 0045，及 ADR 0049 的待处理前提）

本仓实现：

- `LedgerApprovalListReader.pending` 调 `approvalEngine.pendingTasks(username, firstResult, size)` 和 `pendingCount(username)`（`read/LedgerApprovalListReader.java:40-43`）。
- `ProcessEngineApproval.pendingTasks` 用 `taskService.createTaskQuery().taskAssignee(username)`，按任务创建时间倒序 + taskId 决胜（`ProcessEngineApproval.java:33-41`）；`pendingCount` 同一谓词（`:44-48`）。
- 返回后按 `businessKey` 解析账本 id，空 businessKey 跳过（`LedgerApprovalListReader.java:48-55`）；每个任务生成一行（`:57-65`），`ProcessVo.taskId` 是任务级。
- 待处理 total 是任务数，不是审批数（`pendingCount`）。

冲突：

- **只认 assignee，不认候选人**。动态审批人如果落到 `candidateUsers`/`candidateGroups`，任务 `ASSIGNEE_` 为空，`taskAssignee(username)` 查不到；`currentTasks` 也匹配不到。本仓没有 `IdmIdentityService`（§2.3），`candidateGroups` 的组成员无法解析；`candidateUsers` 至少能写入 `ACT_RU_IDENTITYLINK`，但待处理仍查不到。要支持候选人，待处理 port 需要新增 `taskCandidateUser`/`taskCandidateGroup` 查询与 `claim` 动作；ADR 0045 的「五种列表读一个入口」与 ADR 0011 的「当前处理人」定义都要扩。
- **同一审批多任务会多行**。会签/或签/并加签/前加签子任务都会让同一 `processInstanceId` 出现多个 `ACT_RU_TASK`；`LedgerApprovalListReader.pending` 每个任务一行，没有按审批去重；total 计任务数。ADR 0049 明确把「待处理不动」建立在「线性流程同一审批同一时刻只轮到一个节点」上；多任务模型打破这个前提。
- **无 assignee 的挂起任务不可见**。前加签的原任务被 `setAssignee(null)` 后，`taskAssignee(username)` 查不到；原处理人在待处理里看不到这条审批，也无法从详情恢复（`canAct=false`）。
- **独立任务不可见**。如果用 `TaskService.createTaskBuilder()` 创建与流程无关的加签任务，`businessKey` 为空；`LedgerApprovalListReader.pending` 会跳过空 businessKey（`:50-55`），所以这类任务不会出现在待处理。
- **催办标记没有位置**。待处理行形状由 `OaProcessMapper.xml` 的 `approvalRowColumns` 唯一拥有（`:12-17`）；要加「已催办/催办次数」字段，必须改共享列清单与行类型，或在读 module 内按页批量查账本（ADR 0045 允许的批量化模式，但目前没有这个字段）。
- **排序锚点**。待处理按任务创建时间；转办不改变任务创建时间，所以转办不会让任务「变新」；加签子任务会按自己的创建时间插入。产品若要求「催办后置顶/标记」，现有排序不支持。

### 3.5 发布证据（ADR 0046 / ADR 0003）

本仓实现：

- `FlowableProcessDeploymentEngine.deployVerified` 从产物存储打开 zip，`repositoryService.createDeployment().addZipInputStream(zip).deploy()`（`deployment/FlowableProcessDeploymentEngine.java:50-69`）；部署后复查 sha256、按 deploymentId+key 查定义、要求恰好 1 个、检查挂起（`:73-88`）。
- `PublicationVerifier` 校验产物路径/sha256、模板 key 与发布记录 key、记录 definitionId 与引擎定义（`publication/PublicationVerifier.java:57-99`）。
- `ProcessTemplate` 没有审批人/节点配置字段（`model/process/ProcessTemplate.java:17-55`）；只有 `processDefinitionKey`、`processDefinitionPath`、`processModelId`、`formProps/formOptions` 等。
- `application-dev.yml:79` `check-process-definitions: false`，不自动部署 classpath 流程；主路径是上传 zip。
- 仓库 BPMN 示例：`lautung-oa-server/src/main/resources/processes/demo-approval.bpmn20.xml` 用 `flowable:assignee="bob"`；测试用 `activiti:assignee="zhangsan"`/`lisi`（`lautung-oa-server/src/test/resources/process/approval-two-step.bpmn20.xml`）。没有多实例、定时器、动态表达式示例。

冲突：

- **动态审批人/超时必须写进发布时的 zip**。模板没有审批人配置，服务端不会在发布时改 BPMN；`deployVerified` 原样部署。因此：
  - 新模板可以在 zip 里写 `flowable:assignee="${...}"`、TaskListener、边界定时器；
  - 已发布模板被 ADR 0003/0046 冻结，无法追加这些能力；
  - 已发布模板的 `processDefinitionId` 绑定的是旧 zip，重新发布同 key 的新 zip 会产生新定义，旧实例仍走旧定义。
- **发布证据不覆盖解析代码**。`sha256` 只覆盖 zip；如果 zip 里的表达式调用 Spring bean 或 TaskListener 类，解析逻辑在服务端代码里。改服务端代码即可改变已发布审批的实际审批人/超时行为，而发布证据仍然「一致」。这与 ADR 0003「已发布审批步骤保持不变」的事实边界冲突。
- **发布时不校验表达式可解析**。`deployVerified` 只检查定义数量/key/挂起；不会验证 `${deptLeader...}` 在运行时能返回人。发布成功不等于能发起/能路由。
- **撤回/转办/加签/催办本身不改 BPMN**，与 ADR 0046 无直接冲突；冲突集中在「动态审批人」和「超时」这两类需要 BPMN 结构的动作。
- **加签如果通过修改 BPMN 实现**（例如运行时改 outgoing），会直接违反发布冻结；如果通过自定义任务/状态机实现，则发布证据不覆盖这些任务，恢复/重部署时它们不在 BPMN 里。

### 3.6 处理结果待确认（ADR 0004）

本仓客户端合同：

- `Decision` 只有 `processId`、`taskId`、`status: 1 | -1`（`client/guigu-oa-client/src/work/approval-detail.ts:17-20`）。
- `RecoveryRecord extends Decision`，带 `operationId`；`validRecords` 只接受 `status === 1 || status === -1`（`:27-46`）。
- `decide(status: 1 | -1)`（`:220`）。
- 服务端 `Approval.DecisionOutcome` 只有 `processId/status/nextTaskIds`（`service/Approval.java:37-41`）；`ApprovalWriter.approve` 只处理 1/-1（`approval/ApprovalWriter.java:189-218`），`requireStatusInDomain` 只允许 1/-1（`:220-224`）。

冲突：

- ADR 0004 的「处理结果待确认」是为「通过/拒绝」设计的：请求发出后无法确认是否生效，客户端保留恢复记录，离开不撤销。转办/加签/撤回/催办同样是**会改变服务端状态且可能丢响应**的写动作，但现有 `Decision`/`RecoveryRecord`/`DecisionOutcome` 无法表达它们的 action 类型与结果。
- 如果新动作复用 `/approve` 的 status 通道，会与「status 只能 1/-1」冲突；如果新开端点但不定义「待确认」语义，ADR 0004 的恢复合同只覆盖审批决定，不覆盖新动作。事实层面：ADR 0004 没有为这些动作提供现成的结果确认/恢复形状。
- ADR 0004 的「离开不撤销已提交处理」与「撤回」不直接矛盾：撤回是显式动作，不是离开；但它引入「已提交处理可被另一动作回滚」的新事实，需要单独定义「撤回结果待确认」。
- 服务端仍用 `BusinessException` + `Result` 信封；新动作若沿用，HTTP 形状不变，但客户端的失败分类/恢复记录需要扩展。

### 3.7 数据模型与状态

本仓 DDL/实体：

- `oa_process.status` 注释「0：默认 1：审批中 2：审批通过 -1：拒绝」（`model/process/Process.java:53-55`）；V1 DDL `status TINYINT DEFAULT '0'`（`V1__business_schema_and_seed.sql:124-140`）。
- `oa_process.current_auditor VARCHAR(200)`（`V1:134`），实体有 `currentAuditor`（`Process.java:49-51`），但 `ApprovalWriter` 从不写它；只有演示数据播种器写（`DemoScenarioSeeder.java:137,151`）。实际等待人写在 `description`（`ProcessStartSupport.waitingDescription`，`ProcessStartSupport.java:164-169`），由 `ProcessApprovalSupport.applyOutcome` 更新（`ProcessApprovalSupport.java:34-47`）。
- `oa_process_record` 列：`process_id`、`description`、`status`、`operate_user_id`、`operate_user`、公共字段（`V1:145-155`）；`description VARCHAR(255)`；没有动作类型列。
- `oa_process_record` 有 `(operate_user_id, process_id)` 索引（V109），支撑 processed 去重；没有按 `process_id, create_time` 或按类型的索引。
- 客户端状态标签只认 1/2/-1（`client/guigu-oa-client/src/work/work-list.ts:85-99`）；配置实例查询的状态筛选也只有 1/2/-1（`src/config/process-instance.ts` 的 `STATUS_OPTIONS`）。

冲突：

- **撤回需要新状态**。现有 `oa_process.status` 没有「已撤回」；`ProcessApprovalSupport.applyOutcome` 只会写 1/2/-1；客户端 `statusLabel`/`statusTagType`/`STATUS_OPTIONS` 都没有撤回档。撤回后如果只靠引擎历史 `STATE_="cancelled"`，办事页仍显示「审批中」。
- **`current_auditor` 与 `description` 双轨**。转办/加签/撤回后要更新「当前审批人」展示；现有写路径只在 `approve` 后更新 `description`，`current_auditor` 从未写。两条字段的权威性没有合同定义。
- **动作记录没有类型**。转办/加签/催办/撤回都只能塞进 `description`/`status`；processed SQL 又按「非发起申请」全收。ADR 0049 曾考虑给 `oa_process_record` 加记录类型列并以「价值密度低」拒绝；现在动作种类变多，这是事实层面的张力。
- **超时自动动作没有 actor**。`oa_process_record.operate_user_id` 是用户 id；系统动作没有用户 id，现有表不能区分「系统自动通过/拒绝/关闭」。如果写入 null，processed 按用户过滤不会命中，但审计/详情无法显示操作者。
- **附件/抄送**：`glossary` 的「附件」说审批结束后保留；现有撤回路径不会删附件或抄送。若产品要求撤回后撤销抄送可见性，现有 `oa_process_cc` 没有「撤回后失效」语义。

### 3.8 组织模型（ADR 0051 与地图 #267）

本仓数据：

- `sys_dept.leader VARCHAR(50)`（`V110__BackofficePermissionBase.java:35-47`），是姓名文本，不是 `sys_user.id` 外键；`sys_dept` 有 `parent_id`/`ancestors`（含自身的路径）。
- `sys_post`（`V110:50-61`）只有 `post_code/name/sort_value/status`；`sys_user_post`（`:63-72`）是用户-岗位关联。
- 模块地图明确：「岗位只是职务标签，不产生授权、不参与审批分配、不套数据范围」（`docs/agents/client-modules.md:39`）。
- `sys_role` 是后台 RBAC 角色（ADR 0052/0056），`sys_user_role` 是授权关联；不是审批角色。
- `sys_user.dept_id` 是单部门（`V1:7-28` 的 `sys_user` 定义），没有多部门归属。
- ADR 0051 原文：「组织模型：部门与岗位只作组织数据与用户归属；审批链继续由 BPMN 指定用户名，本轮不让部门/岗位成为任务分配依据——改 `candidateGroups` 会牵动成员资格证据、待处理读、认领动作与写侧，等于重做审批核心的一块。」地图 #267 的 Notes 已明确「重开 ADR 0051 的边界」：动态审批人按部门负责人/角色/岗位解析进第一波。

冲突：

- **`sys_dept.leader` 不能可靠解析成用户**。它是姓名，可能重名、改名、离职；没有用户 id 外键。要按部门负责人解析，需要先把 leader 升级为用户引用（或新增字段/关联表），这是数据模型变更。
- **岗位不是审批人类型**。`sys_post` 只是标签；要用岗位做审批人，需要新语义（岗位成员集合、是否含负责人、多岗位用户如何选人），现有表只有 `sys_user_post` 关联。
- **角色语义冲突**。`sys_role` 是后台授权角色；用它做审批人会把「能进哪个页面/按钮」与「审批链上是谁」绑定。ADR 0052/0056 的权限模型没有为「审批角色」留独立维度。
- **发起人上级没有数据**。没有 `manager_id`/`leader_id`；只能从 `sys_dept.parent_id`/`ancestors` 找上级部门，再读该部门 `leader` 姓名，且姓名还要反查用户。飞书的「连续多级上级/连续多级部门负责人」在本仓没有对应数据结构。
- **多部门**。飞书 FAQ 有「提交人存在于多个部门时，审批会流转给哪位部门负责人」的问题，说明多部门是现实场景；本仓 `sys_user.dept_id` 单值，没有多部门归属。
- **ADR 0051 与地图 #267 的边界冲突是显式的**：ADR 0051 说「本轮不让部门/岗位成为任务分配依据」；#267 说「动态审批人按部门负责人/角色/岗位解析进第一波」。这是决策票 #271 必须处理的合同修订/废止点。

### 3.9 运行与测试配置

- dev：`flowable.database-schema-update=true`、`db-history-used=true`、`history-level=full`、`check-process-definitions=false`；未设 `async-executor-activate`（`application-dev.yml:75-79`）。Spring Boot 属性默认 `true`（`FlowableProperties.java:41`），所以 dev 的定时器默认可触发。
- test：同上，额外 `async-executor-activate: false`（`application-test.yml:23-28`）。合同测试里边界定时器不会自动触发；超时相关测试需要显式开启或直接执行 job。
- `history-level=full` 保证 `ACT_HI_*` 全量；但本仓不设 `Authentication`，所以引擎侧用户字段为空（见 §2.3）。
- 现有 Flowable 事件监听器只有抄送：`CarbonCopyOnTaskCreated` 监听 `TASK_CREATED`（`cc/CarbonCopyOnTaskCreated.java:29-58`），由 `CarbonCopyEngineConfiguration` 注册（`cc/CarbonCopyEngineConfiguration.java:15-26`）。它 `isFailOnException=false`，异常只 WARN，不阻断任务创建。动态审批人如果也挂 `TASK_CREATED`，与抄送共享同一事件；解析失败会静默留下无 assignee 的任务。
- 合同测试 schema 是独立的 `lautung-oa-server/src/test/resources/approval-contract-schema.sql`；新增列/状态要同时改 Flyway 与测试 schema（否则 H2 合同测试先炸）。

---

## 4. 未取证与事实缺口

以下是研究过程中确认「一手文档没有给出」或「需要运行验证」的点，供 #271 判断风险，不构成建议：

1. **钉钉的「前/后加签完成后回到谁」**：加签 API 文档只给 `before`/`after` 与审批方式，没有文字说明前加签是否回到当前审批人、后加签当前节点是否自动通过。飞书和 yudao 有明确说明；钉钉只能从产品行为推断。
2. **钉钉的催办与委托**：开放平台 OA 审批 API 清单没有催办/委托端点；只有限时审批事件。产品界面是否存在、规则如何，未在开放文档中找到一手依据。
3. **钉钉超时自动通过/拒绝**：限时审批事件示例的 `actionType` 是 `remind`；开放文档没有列出 `autoApprove`/`autoReject` 一类动作值。节点级 `AUTO_AGREE`/`AUTO_REFUSE` 是审批类型，不是超时规则。
4. **飞书超时自动通过**：FAQ 明确「不可以」；只有审批人为空兜底自动通过。若 #271 打算做「超时自动通过」，这是与飞书语义相反的产品选择，需要明确记录。
5. **或签销毁任务的 delete reason 具体值**：多实例完成条件销毁剩余实例时，`ACT_HI_TASKINST.DELETE_REASON_` 的写入值需要跑一个真多实例用例确认；本文只确认「会销毁、历史行保留」。
6. **Flowable 顺序多实例 + `addMultiInstanceExecution`**：源码只对 `!isSequential()` 继续；顺序多实例加签的行为需要实测。本文只报源码事实。
7. **撤回后附件/抄送的处置**：三家产品文档都没有完整规则；本仓现有撤回路径（`deleteProcessInstance`）不会改 `attachment`/`oa_process_cc`。产品要什么，需要 #271 定。
8. **转办后原处理人可见性**：三家产品文档都没有明确；本仓成员资格模型目前会直接 403。这是 #271 明确列出的待决问题。
9. **「岗位」在钉钉/飞书的审批人类型**：钉钉流程预测 API 只文档化 `allStaff/approvals/labels`；飞书审批人列表没有岗位。不能据此断言产品界面没有，但开放文档没有一手依据。
10. **本仓已发布模板的数量与形态**：没有统计；但仓库示例 BPMN 全是单 userTask、字面量 assignee，没有多实例/定时器/表达式。动态审批人与超时对既有模板的覆盖率需要另查生产数据。

---

## 5. 来源清单

### 5.1 钉钉开放平台

- [OA审批开放能力汇总](https://open.dingtalk.com/document/orgapp/oa-approval-open-capacity-summary)
- [加签审批任务](https://open.dingtalk.com/document/orgapp/api-premiumappendtask)
- [转交OA审批任务](https://open.dingtalk.com/document/orgapp/transfer-the-oa-approval-task)
- [撤销审批实例](https://open.dingtalk.com/document/orgapp/revoke-an-approval-instance)
- [同意或拒绝审批任务](https://open.dingtalk.com/document/orgapp/approve-or-reject-the-approval-task)
- [获取审批单流程中的节点信息](https://open.dingtalk.com/document/orgapp/approval-process-prediction)
- [OA限时审批事件变更](https://open.dingtalk.com/document/orgapp/events-oa-timeout-plugin-task-msg)
- [评论及撤销审批流](https://open.dingtalk.com/document/orgapp/comment-and-revoke-approval-flow)
- [发起及撤销审批流](https://open.dingtalk.com/document/orgapp/new-version-of-initiate-and-cancel-approval-flow)

### 5.2 飞书开放平台与帮助中心

- [审批概述](https://open.feishu.cn/document/server-docs/approval-v4/approval-overview)
- [审批任务加签 API](https://open.feishu.cn/document/server-docs/approval-v4/task/approval-task-addsign)
- [转交审批任务 API](https://open.feishu.cn/document/server-docs/approval-v4/task/transfer)
- [撤回审批实例 API](https://open.feishu.cn/document/server-docs/approval-v4/instance/cancel)
- [同意审批任务 API](https://open.feishu.cn/document/server-docs/approval-v4/task/approve)
- [管理员设置转交、加减签、回退审批](https://www.feishu.cn/hc/zh-CN/articles/360049067381)
- [管理员设计审批流程](https://www.feishu.cn/hc/zh-CN/articles/360036163653)
- [管理员设置审批人异常流转规则](https://www.feishu.cn/hc/zh-CN/articles/360049067817)
- [管理员设置审批超时规则](https://www.feishu.cn/hc/zh-CN/articles/852878870528)
- [飞书审批常见问题](https://www.feishu.cn/hc/zh-CN/articles/360045410394)
- [管理员设置允许审批人撤回](https://www.feishu.cn/hc/zh-CN/articles/491253928695)
- [飞书审批成员手册](https://www.feishu.cn/hc/zh-CN/articles/102254611657)

### 5.3 ruoyi-vue-pro（yudao）BPM

- [BPM 文档索引](https://doc.iocoder.cn/bpm/)
- [审批转办、委派、抄送](https://doc.iocoder.cn/bpm/task-delegation-and-cc/)
- [审批加签、减签](https://doc.iocoder.cn/bpm/sign/)
- [会签、或签、依次审批](https://doc.iocoder.cn/bpm/multi-instance/)
- [选择审批人、发起人自选](https://doc.iocoder.cn/bpm/assignee/)
- [流程发起、取消、重新发起](https://doc.iocoder.cn/bpm/process-instance/)
- [审批通过、不通过、驳回](https://doc.iocoder.cn/bpm/task-todo-done/)
- 源码：[`BpmTaskCandidateStrategyEnum.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/enums/BpmTaskCandidateStrategyEnum.java)、[`BpmTaskCandidateInvoker.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/BpmTaskCandidateInvoker.java)、[`BpmTaskCandidateAssignEmptyStrategy.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/strategy/other/BpmTaskCandidateAssignEmptyStrategy.java)、[`BpmUserTaskActivityBehavior.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/behavior/BpmUserTaskActivityBehavior.java)、[`BpmProcessInstanceServiceImpl.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java)、[`BpmTaskServiceImpl.java`](https://github.com/YunaiV/ruoyi-vue-pro/blob/master/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java)

### 5.4 Flowable 8.0.0

- [BPMN 2.0 Constructs（User Task / Multi-instance / Boundary Events / Timer）](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs)
- [History](https://www.flowable.com/open-source/docs/bpmn/ch10-History)
- [The Flowable API](https://www.flowable.com/open-source/docs/bpmn/ch04-API)
- 源码：[`TaskService.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/TaskService.java)、[`RuntimeService.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/RuntimeService.java)、[`DelegationState.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-task-service-api/src/main/java/org/flowable/task/api/DelegationState.java)、[`TaskHelper.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/util/TaskHelper.java)、[`CompleteTaskCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/CompleteTaskCmd.java)、[`DelegateTaskCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/DelegateTaskCmd.java)、[`ResolveTaskCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/ResolveTaskCmd.java)、[`AddCommentCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/AddCommentCmd.java)、[`DeleteProcessInstanceCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/DeleteProcessInstanceCmd.java)、[`AddMultiInstanceExecutionCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/AddMultiInstanceExecutionCmd.java)、[`DeleteMultiInstanceExecutionCmd.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/DeleteMultiInstanceExecutionCmd.java)、[`DefaultHistoryManager.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/history/DefaultHistoryManager.java)、[`DefaultIdentityLinkInterceptor.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/java/org/flowable/engine/impl/interceptor/DefaultIdentityLinkInterceptor.java)、[`FlowableProperties.java`](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-spring-boot/flowable-spring-boot-starters/flowable-spring-boot-autoconfigure/src/main/java/org/flowable/spring/boot/FlowableProperties.java)
- DDL：[history](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/resources/org/flowable/db/create/flowable.mysql.create.history.sql)、[engine](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine/src/main/resources/org/flowable/db/create/flowable.mysql.create.engine.sql)、[common](https://github.com/flowable/flowable-engine/blob/flowable-8.0.0/modules/flowable-engine-common/src/main/resources/org/flowable/common/db/create/flowable.mysql.create.common.sql)

### 5.5 本仓库

- `docs/adr/0011-审批成员资格收成一个gate且展示允许比动作粗.md`
- `docs/adr/0004-审批处理结果待确认且离开不撤销.md`
- `docs/adr/0045-审批列表读收成一个读module且行形状窗口回查排序一处拥有.md`
- `docs/adr/0046-发布一致性收成判定module且部署收进引擎adapter.md`
- `docs/adr/0049-已处理行单位是审批且读证据由账本动作记录供给.md`
- `docs/adr/0003-审批模板发布冻结且恢复不得猜测.md`
- `docs/adr/0051-产品形态改为桌面优先泛OA后台且撤销统一客户端约束.md`
- `GLOSSARY.md`（「审批」「待处理」「处理审批」「已处理」「附件」「审批成员资格」）
- `server/lautung-oa-parent/lautung-oa-process/src/main/java/com/lautung/oa/process/`（`approval/ApprovalEngine.java`、`ApprovalWriter.java`、`FlowableApprovalEngine.java`、`ProcessEngineApproval.java`、`membership/`、`read/`、`cc/`、`deployment/`、`publication/`）
- `server/lautung-oa-parent/lautung-oa-model/src/main/java/com/lautung/oa/model/process/Process.java`
- `server/lautung-oa-parent/lautung-oa-server/src/main/resources/db/migration/V1__business_schema_and_seed.sql`
- `server/lautung-oa-parent/lautung-oa-server/src/main/java/db/migration/V110__BackofficePermissionBase.java`
- `server/lautung-oa-parent/lautung-oa-server/src/main/resources/application-dev.yml`、`src/test/resources/application-test.yml`
- `client/guigu-oa-client/src/work/approval-detail.ts`、`src/views/work/pending/detail.vue`、`src/work/work-list.ts`、`src/config/process-instance.ts`
- `docs/agents/client-modules.md`
