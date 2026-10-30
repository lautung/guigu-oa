# 服务端按 form-create 语义校验发起表单必填

模板 `formProps` 里的 `$required` 过去只在浏览器生效（ADR 0033 留给 #153）：客户端 form-create 会拦，服务端把 `form_props` 当不透明字符串透传，绕过客户端即可把必填字段写成空值。决定：服务端在 `ApprovalWriter.startUp` 里第一次解释 formProps，按 form-create 的语义做必填校验，失败是明确的 201 业务失败——HTTP 200 + `code=201` + 指向字段的中文消息，不产生审批（仍在幂等重放早退之后、任何写入与引擎启动之前，201 的含义是「什么都没发生」；客户端现有分类把它归为 refused 回编辑态，不进发起结果待确认）。

口径是**镜像客户端**（server ⊆ client），保证服务端永远不会拒绝一个同样提交在客户端会被放行的请求：

- 必填形状：`$required === true`、`$required` 为非空字符串（其值即校验消息）、`effect.required === true`；`$required === false` 或缺省不算；递归遍历 `children`；没有 `field` 的布局节点无从取值，跳过。
- 空值定义与 form-create 的 `is.empty` 完全一致：键缺失、`null`、`''`、`[]` 算空；纯空白串、`0`、`false`、`{}`、非空数组不算空，不 trim。模板有必填字段而 `formValues` 为空（或 `formData` 缺失）按全部缺失计——#148 只保证非空 formValues 的 JSON 形状，这条空子在这里堵上。
- 静态判定范围：`hidden: true`、`type: 'hidden'` 跳过（客户端不渲染、不校验）；`display: false` 客户端仍校验，这里也校验；带 `control` 的规则整体跳过、不求值条件——条件在服务端无法求值，硬校验会把客户端按条件藏起来的字段死锁在发起页。用 control 动态关必填的模板暂不受服务端保护，这是已知宽松面。
- 消息：字段名取 `rule.title`，缺则退 `rule.field`；默认 `{title}不能为空`，`$required` 为字符串时用该字符串；多条按规则顺序用「；」连接（`ValidationMessages.join`）。
- formProps 解析失败按无约束处理（fail-open）：这种模板客户端同样渲染不出来，用户到不了提交；#153 的职责是解释已发布模板的约束，不是修复模板。

## 考虑过的替代方案

- **从紧的空值定义（服务端 trim 后判空）**：会造成「客户端放行、服务端拒绝」的错位；要收紧空白串必须两端一起改，另行处理。
- **对 `control` 条件求值以完全镜像**：条件表达式求值是 form-create 的运行时行为，服务端重写一份成本高且易漂移；而硬校验会死锁合法用户，跳过只影响未来使用 control 的模板。
- **formProps 解析失败 fail-closed（拒绝发起）**：把模板配置缺陷当成用户提交的错误；这类模板本就没有可用的发起入口。
- **把校验提前到 HTTP 边界（jsr303 或 #148 的结构校验旁）**：必填字段来自已发布模板，边界上拿不到模板数据；放在 `ApprovalWriter` 和幂等键同处，`DemoScenarioSeeder` 这类直接调用方也受保护（与 ADR 0033 的结构校验同一理由）。

## 后果

- 服务端首次依赖 formProps 的 JSON 形状：改解释口径会影响存量已发布模板，改动须按本 ADR 的镜像原则评估。
- 客户端零改动：校验失败的 201 已被 `submission-outcome` 分类为 refused，回编辑态并显示消息。
- 现有发起链路不受影响：合同 schema 的模板没有 `form_props`（null → fail-open），发起页 UX 不变。
- 未做：`control` 条件必填的服务端求值、空白串收紧（需两端一起）、必填之外的其他动态约束（长度、格式等）。
- 回归锚点：`ProcessStartSupportTest`（形状、空值、隐藏与条件、fail-open）、`FormRequiredContractTest`（两种模板 + 201 信封 + 写入前拒绝）。
