# 请求作用域收成一个 module，domain 配置只留传输字段

候选 3 指出「迟到结果不能落地」的归属规则在五个调用点各拼一遍：`ApiRequestConfig` 带着 `_recognition`、`_localErrors`、`_isRelevant` 与 `normalErrorNotification` 四个传输层字段，`list-read`、`template-editor`、`approval-detail-http`、`approval-start-browser`、`assign-roles-browser` 分别手工填写；其中 `_localErrors: true` 与 `normalErrorNotification: 'caller'` 在 `rejectRequest` 里是同一种行为，只是两套拼写。现在把「这次请求属于哪次打开、失败该不该全局提示」收进 `src/request-scope.ts`：`openRequestScope(recognition, { notifyNormalErrors })` 在打开时捕获一次身份，返回的 `request(config, isRelevant?)` 把捕获结果、按次相关性与错误策略拼进传输层配置；公开的 `ApiRequestConfig` 只剩 `method`、`url`、`data`、`params`，四个内部标志移入 `utils/request.ts` 导出的 `ScopedRequestConfig`，只允许 scope 生产。

捕获与相关性分成两层：身份在打开时锚定（ADR 0009 的「创建时捕获一次」不变），相关性按次传入——`list-read` 传本次读取的 sequence，`template-editor` 把 `current()` 与本次上传编号组合后传入，`approval-detail` 传它自己的 `isCurrent`。`scope.current()` 用 `assertCurrent` 回答这次打开是否仍属于当前身份。`approval-start` 与 `assign-roles` 的 facade 不拥有打开，每次 adapter 调用现开一个 scope，捕获发生在调用时，与它们原先依赖拦截器发送时捕获的行为一致；它们不新增相关性判决，迟到归属仍由各 module 的 `owns` / `sameIdentity` 后置判断负责。

纯 module 改为接 `openScope: () => RequestScope`：`createListRead` 在创建时调用一次，`createTemplateEditor.open()` 每次打开调用一次，`createHttpDetailAdapter.forOpening` 每次打开调用一次。模板编辑与审批详情仍需要一个 `entryId()` 端口来区分「身份被替换」与「身份已清空」——那是身份观察，不是请求归属，因此不并进 scope。传输层保留未显式给上下文时的默认捕获，供 `api/*` 旧包装与登录/refresh/info 继续使用；`_isRelevant` 的两个用途（压掉普通 toast、跳过 208 的一次认证恢复重试）仍由同一个谓词承担，ADR 0004/0008 的失败分类不变。

否决的替代方案：每次操作开一个新 scope 会把捕获改成按次，`list-read` 的打开锚定与现有合同测试都要改；把四个标志留在 domain 类型只是搬家，调用点仍各自拼；让 module 内部 import 传输层则单测无法替换 seam，候选的 leverage 落空。

代价是三个纯 module 的 options 形状变化、六个列表调用点与两个视图各改一行接线；测试面按层拆开：新增 `request-scope.spec.js` 证明捕获一次、按次相关性、错误策略映射与 `current()`，`list-read` 与 `template-editor` 的测试改为断言假 scope 收到的配置与谓词，`recognition` 的传输层用例改用内部字段。e2e 不新增，现有五个路径继续覆盖。

Related: ADR 0004/0008（失败分类与待确认）；ADR 0009（列表打开时捕获一次）；ADR 0012（审批写入端口）。
