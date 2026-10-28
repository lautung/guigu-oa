# 列表页的读后状态与写后策略收成一个 module

候选 5 指出 `createListRead` 只收了口令与竞争：六个列表页仍各写一套 `view`/`rows`、行号、`el-pagination`、空态、`retry`、写后刷新与失败策略。删除操作统一 `.catch(() => {})`（顺带吞掉确认框取消），而 `save()`、`publish()`、状态切换与 `sysMenu` 的 `fetchTree()` 没有 catch，失败时留下 unhandled rejection；`sysMenu` 另外没有错误展示与空态。现在把读后状态与写后策略收进 `src/list-page.ts` 的 `createListPage`：它接收 `openScope`、`toRequest`、可选的记录级 `present` 与注入的 `notify` / `confirm`，拥有 `view`、`rows`、`emptyText`、`rowIndex`、`search(query)`（回到第 1 页）、`retry` / `refresh`、页码与页大小命令、`runWrite`、`confirmRemove` 与 `settle`。五个配置列表页与 `WorkListPage` 改用这个 module，页面只保留筛选状态、列定义与编辑弹窗；`WorkListPage` 的卡片行仍由它自己的 `presentWorkList` 计算。

失败策略固定为一种：列表读失败只呈现内联 `ListLoadError` 与重试（请求 scope 已压掉全局 toast）；写失败由请求层全局提示，`runWrite` 返回 false、不刷新、不再抛；确认框取消不写也不提示；一次性读取（如 `getById`）用 `settle` 收敛为 `undefined`，不再留 unhandled rejection。`notify` / `confirm` 注入保持工厂可在 interface 上单测，浏览器接线放在 `src/list-page-browser.ts` 的 `listPageEffects()`。

分页标记抽成 `ListPagination` 展示组件（props 为 `page` / `size` / `total`，`compact` 去掉页大小与跳页），五个配置页与 `WorkListPage` 共用；表格模板仍留在页面，本轮不抽表格 shell。`sysMenu` 是树读、不分页，不强行套进 `createListPage`：它只补上读错误状态与重试、空态，以及写链的失败终态（确认框改用同一套 effects）。

否决的替代方案：只抽一个 Vue composable、页面保留全部 `view/rows/methods`，共享的仍只有 opening 创建，候选指出的分页、写后刷新与失败策略重复不变；连表格 shell 一起抽，列插槽（图标、状态、操作按钮）需要层层转发，收益与风险不匹配；把 `sysMenu` 强行做成 paged 列表，会给树读引入无意义的页码。

代价是五个配置 module 的私有 `presentListItem` 改为导出的 `presentListRecord`，`presentListPage` 保留为调用同一函数的薄包装（现有 presenter 单测不动）；页面从 Options API 的 `computed`/`methods` 改为从 setup 取得 `list`、`view`、`rows`、`emptyText`。测试新增 `list-page.spec.js` 覆盖自动读取、搜索与页大小回到第 1 页、`runWrite` 成败、`confirmRemove` 取消、`settle` 与释放；e2e 不新增，现有页面用例继续覆盖接线。

Related: ADR 0009（列表读取收口）；ADR 0016（请求作用域）；ADR 0014（配置菜单授予）。
