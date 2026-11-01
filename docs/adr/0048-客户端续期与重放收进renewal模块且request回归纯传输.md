# 客户端续期与重放收进renewal模块且request回归纯传输

`src/utils/request.ts` 在第三轮架构审查（#177，`163b0fb` 时点 279 行）时是全客户端最大的无专属测试 module，里面藏着一整套续期状态机：单飞去重（`refreshFlight`）、换头重放（`_authRefreshRetried` 标记 + 并发已刷新则直接换头）、信封谓词（`businessEnvelope`），外加错误分类与 axios 骨架。续期是 ADR 0037（#155）刚落过 401 家族语义、又最缺直接测试面的地方——三条规则只能借道 `recognition.spec` 的集成面碰。

决定:**续期收进 `src/renewal.ts`，错误家族与信封谓词收进 `src/request-failure.ts`，`request.ts` 回归纯传输**（axios 实例、拦截器骨架、附头、`isInfo`、转发；279 → 108 行）。

- `renewal.ts` 是续期状态机的唯一 owner:`createRenewal(recognition, resend)` → `onExpired(config, context)`。208 的决策与执行整体在这里:豁免（认证端点、已重试、`_isRelevant() === false`）返回 null 交回调用方按业务失败拒绝；并发已刷新则直接换头重放；否则单飞 refresh（裸 `axios.post` 打 `refreshUrl`，避开拦截器循环）后换头重放。refresh 失败先按上下文上报 `recognition.reject`，再以「原请求被明确拒绝」的形状抛 `BusinessRequestFailure`。
- `isAuthentication` 随重放豁免归 renewal 导出（login 208 是凭证错、refresh 208 是续期本身、logout 是主动退出——不重放的理由在续期语义里）；`isInfo` 留 request.ts（silent capture 的理由是「身份读失败走认出恢复」，与续期无关）。两谓词分家，各随其消费语义。
- `request-failure.ts` 收 `RequestFailure` / `BusinessRequestFailure` / `businessFailure` / `transportFailure` / `businessEnvelope`，以及 `rejectRequest`（失败上报 recognition 的统一接线，request 与 renewal 两处共用）。信封谓词判定的是「HTTP 4xx 带不带业务信封」这个传输形状（#145/#155 两个入口共用），不是续期语义，故不随续期走；放独立文件是因为 renewal 需要在运行时抛这些类，留 request.ts 会让 `request → renewal → request` 成环。消费方（`submission-outcome.ts` 及两个 spec）直接改 import，不留兼容 re-export。
- 依赖方向无环:`request → renewal → request-failure`，`request → request-failure`，`renewal → recognition`。`request-scope`（ADR 0016）一行未动，`ScopedRequestConfig` 仍在 request.ts。

## 考虑过的替代方案

- **只收单飞去重，重放编排留拦截器**:request.ts 仍拖着半个状态机，不满足 issue 目标。
- **renewal 吃整个信封（含 200 直通与非 208 拒绝）**:「200 直通、按业务码拒绝」是传输策略，塞进续期会越过 request-scope（ADR 0016）的错误策略界。
- **isInfo/isAuthentication 强并一处**:「同一族 URL 一个 home」的表面整齐，代价是 isInfo 的认出语义跟到续期里。
- **错误家族留 request.ts 并导出**:renewal 反向 import 成环；ESM 循环虽然可运行，但类型擦除依赖打包器行为，不留这种隐患。
- **rejectRequest 注入 createRenewal**:interface 从两个依赖变三个，而它本就是「失败如何上报」的失败语义，归 request-failure 更贴。

## 后果

- `tests/unit/renewal.spec.js` 直接打 renewal interface:单飞去重、迟到 208 复用新头、豁免三连、#155 失败清理、超时保凭证（GLOSSARY「认出」红线:临时故障不踢出）、缺 token 对、单飞清理、superseded 迟到结果不导航——不再借道视图或 recognition 集成面。
- `recognition.spec.js` 的续期用例保留不动:它们打的「recognition × 真实传输」集成面依然存在，与新 spec 互不替代。
- `setRequestToken` 归 renewal 导出、请求拦截器复用:换头与附头是同一个动作（向请求出示凭证），凭证提交点只剩 renewal 一处。
- 回归锚点:`npm run verify`（394+ unit + 新续期 spec）、e2e 60 条（登录/退出/改密走真实续期链路）；服务端未动，`f-http-semantics` 的 401 形状由 ADR 0037 在服务端钉着。
