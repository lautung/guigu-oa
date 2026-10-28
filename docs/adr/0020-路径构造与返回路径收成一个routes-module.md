# 路径构造与返回路径收成一个 routes module

候选 7 指出办事路径的知识散在三处：`navigation/constants.ts` 的 `ROUTES`、`router/routes.ts` 的子路由（`pending`、`pending/:id/:taskId`…）、`work-list.ts` 的 `detailPath` 模板；`approval-detail.ts` 又写死 `/work/processed` 与 `/work/pending`，详情页从 surface 间接推断返回路径并兜底 `/work/pending`；配置侧同样分散（`sys-role.ts` 的 `assignAuthPath`、`process-instance.ts` 的 `instanceDetailPath`、`template-editor.ts` 的列表常量、`processTemplate` 视图的两处 push、`assignAuth` 的返回字面量、`templateSet` 的路径比较）。没有任何测试把生成的详情路径与路由表对上。现在新增 `src/router/paths.ts` 作为唯一 owner：`PATHS` 声明两组（办事与配置）的绝对路径与动态模式，`ROUTES` 保留既有形状，`WORK_LIST_KINDS` / `WorkListKind` 一并移入；构造器 `workListPath`、`workDetailPath`、`startApplyPath`、`processTemplateListPath`、`templateSetPath`、`assignAuthPath`、`processInstanceDetailPath`、`sysRolePath` 都在这里；`workListKindOf` 从路由 meta 读列表种类，`returnPathOfRoute` 回答返回路径。

`router/routes.ts` 用 `childPath(parent, absolute)` 从 `PATHS` 的绝对路径派生每个子路由的 `path`，父组、`redirect` 与 `hiddenParentPath` 也取自 `PATHS`，URL 形状与路由名不变。`navigation/constants.ts` 改为 `export { ROUTES } from '@/router/paths'`，内置菜单、白名单与默认登录路由仍留在 constants。`work-list.ts` 保留标题、空态、emphasis 与记录映射，`detailPath` 改取 `workDetailPath`、`returnPath` 改取 `workListPath`；`workListPerspectiveFromRoute` 接收 `{ meta, path }`，办事详情按 kind 返回列表路径，配置实例详情（kind 为 null）返回 `/processMgr/process`，不再出现空返回路径。`client-surface` 的 `returnPath` 改为 `returnPathOfRoute(path)`，与详情页共用同一份判断；`pending/detail.vue` 删除 surface 回退与 `/work/pending` 字面量；`approval-detail.ts` 的两个恢复去向改取 `workListPath(...)`。

返回路径规则固定为：列表与静态页回自身，详情回所属列表（含配置实例详情），隐藏页回可见父级（`assignAuth` → `/system/sysRole`，`templateSet` → `/processSet/processTemplate`），未知路径回待处理。配置构造器迁出领域模块后，`start-flow.ts` 只剩一个函数，随 `applyPath` 一起删除；`sys-role.ts` 与 `process-instance.ts` 不再导出路径函数。

否决的替代方案：只加「构造结果能解析到路由」的测试、字符串仍各写一遍——候选要消除的重复不变；把构造器直接放进 `router/routes.ts`——纯路径逻辑与 vue-router 表绑在一起，列表展示与配置授予都会拖上整张表；把列表标题、空态等页面文案搬进 routes——路由层不该拥有产品文案。

代价：`ROUTES` 现在从 paths 再导出，`work-list.ts` 不再导出 kind（调用方改从 paths 导入）；`WorkListDefinition` 失去 `returnPath` / `detailPath` 字段。新增 `tests/unit/router/paths.spec.js`：用真实 `constantRoutes` 建 router，逐条断言列表/详情/构造器结果都能解析到对应路由与 meta，并覆盖全部返回路径规则与「配置实例详情返回列表」的视角；原先散在 `process-instance`、`sys-role`、`start-flow` 单测里的路径断言移入这里，e2e 不新增。ADR 0009 的列表读取不变。

Related: ADR 0009（列表读取收口）；ADR 0014（路由组归属来自路由表）；ADR 0018（列表页 module）。
