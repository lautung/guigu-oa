# 发布一致性收成一个判定 module，部署收进引擎 adapter

第三轮架构审查（#175）核出：`OaProcessTemplateServiceImpl` 357 行混了至少五类职责——分页/详情读、
保存/发布状态机、zip 上传与 Flowable 部署校验、发布证据（sha256/绑定一致性）、切库恢复+可发起性。
ADR 0003 是全仓库最早也最硬的不变量之一（发布冻结且恢复不得猜测），但它活在四段记忆里而不是一个
module 的 interface 里：校验散在 publish / restore / currentDefinition / deployVerified 四段，
改一次发布语义要读全类。

决定（#187–#190，随 #191 收尾）：

**发布判定收进 `PublicationVerifier`（窄判定）**。它只回答一个问句：「这份模板的发布证据与引擎
绑定当前是否可用、什么理由」。输入模板与发布记录，输出结构化 verdict：证据问题理由（七条原文之一
或 null——票面写「六条」系计数偏差，实际 `publicationEvidenceProblem` 有七个非空分支，逐字保留）
+ 绑定三态（`DefinitionMissing` / `Inconsistent(reason)` / `Usable(definition)`，sealed interface
编码不变式：可用必带定义、证据问题时绑定 null 且不触引擎）+ 可用时定义（id/key/deploymentId/
suspended）。零 Flowable 类型、零业务异常，引擎交互只走部署 port 的 `definitionById`。发起解析与
可用性标注（分页/详情/按 key 查已发布/可发起分组/发起解析）改打 verdict，四个散布私有判定方法删除；
归并行为不变——两类问题对外都只报「暂不可发起，需核实原审批内容」。

**部署动作收进 `ProcessDeploymentEngine` port + Flowable adapter**。port 两方法（`definitionById`
按 definitionId 查定义、`deployVerified` 按路径与 key 部署），不暴露 Flowable 类型；唯一实现
`FlowableProcessDeploymentEngine` 持有 RepositoryService 与产物存储，`deployVerified` 方法体逐字
随迁（部署期摘要前后校验、内容寻址复查、定义计数、挂起检查与全部文案不动）。`publish` 与
`restorePublished` 改调 port，模板 service 不再注入 RepositoryService。Flowable API 触点由
「唯一 `ProcessEngineApproval`」收为两处：审批运行时 port + 部署 adapter——spec 原红线由票 #188
显式取代，模块地图（`docs/agents/client-modules.md`）同步记录两处触点。

**切库恢复编排切 verdict 四分支**：证据问题 → 停止；绑定不一致 → 停止；可用且部署绑定空 → 按记录
definitionId 查得定义后回填（可靠依据非猜测，ADR 0003）；定义缺失 → 经 port 重部署。停止文案逐字
（「切库恢复停止：…（理由）」内嵌结构），批量全部成功才生效语义不变。编排自拼的 definitionById
直查与 key/deploymentId/suspended 手工比对删除，#189 过渡期「证据正常时 verify 与恢复各查一次引擎」
的双重查询随之消解（一次 verify 一次查询）。

**死方法清理**：模板 service 接口的 `canInitiate(Long)` main 零调用方（controller 走静态
`ApprovalTemplateSupport.canInitiate(Integer)`），删除，接口降为 11 方法。

窄判定 vs 宽 owner 的取舍：宽 owner（把 publish/restore 状态机一并收进 module）被拒——编排是
流程性代码，收进判定 module 会让它同时拥有「判断」与「行动」，发布状态机的每次改动都要穿过判定
接口，测试面随之变大。verdict 是纯数据、零异常，三个消费方（发起、可用性标注、恢复编排）共用
同一判定而不共享执行流；模板 service 回到读 + 状态机 + 恢复编排（357 → 284 行），zip 部署的
Flowable 交互另立 adapter。

## 考虑过的替代方案

- **宽 owner：发布/恢复状态机也收进 module**：一个 module 拥有发布语义听起来更「深」，但判断与
  行动同体后，状态机的执行流变化会不断穿透判定接口；拆开后改发布语义只动 verdict 判定，改编排只动
  service，改引擎交互只动 adapter。
- **verdict 直接返回 port 的 `DefinitionSnapshot`**：四字段全同，但 verdict 自有 record 让判定
  module 与 port 各自演化，port 改签名不外溢到判定消费方；映射成本一行。
- **`Inconsistent` 无参化**（评审建议）：reason 当前只有一个值（`UNUSABLE_BINDING`，已收窄为
  private），但票 #189 AC 明文「绑定不一致+理由」，留形状给未来理由扩位。
- **恢复回填改为重新部署**：会覆盖引擎里同 key 定义、丢恢复语义；按记录 definitionId 回填才是
  ADR 0003 的「不得猜测」。

## 后果

- 对外行为零变化：七条证据理由、归并文案、停止文案逐字；合同测试原样绿。「已绑定的流程定义当前
  不可用」降为内部理由标记（`UNUSABLE_BINDING`），不再作为对外/恢复文案出口。
- verdict「可用」涵盖 recorded deploymentId 为空的半态（恢复回填分支需要）；发起侧保留「部署绑定
  必须完整」前置 guard（发布即写 deploymentId，缺失即记录不完整），行为与改动前逐字一致。
- 理由矩阵由 `PublicationVerifierTest`（真产物存储临时目录 + fake 引擎 port，8 用例）全分支钉住，
  含「可用但部署绑定缺失」场景；port 合同测试 9 用例；恢复四分支各有钉子（含单查询断言）。
- 回归四件套（#191）：服务端 492、客户端 verify 511、接口套件 2×76、功能栈 28 全绿；执行记录
  [2026-11-01-publication-module.md](../test/execution/2026-11-01-publication-module.md)，证据
  `docs/test/evidence/2026-11-01-publication-module/`。功能栈首轮 27/28 为既存测试竞态
  （#192，附件确认信号），已修并复跑绿，与本 ADR 改动无关。
- 下一处改发布语义只动 `PublicationVerifier` 一处；引擎升级或部署产物存储变更只动
  `FlowableProcessDeploymentEngine`；给发起/恢复加新的可用性理由只扩 verdict，消费方文案归并不动。

**2026-11-06 修订（#271）**：证据核对覆盖发布产物（流程 zip 与审批人规则 sidecar 的合成件）；节点规则缺失、目标不存在或停用的发布校验在部署前拒绝；verdict 的三态形状不变。见 ADR 0069。

Related: #175、#187、#188、#189、#190、#191、#192；ADR 0003、0005、0019；GLOSSARY「已发布」「发布证据」「流程 zip」。
