# 发起草稿由 module 拥有，facade 只做 form-create 的绑定 adapter

候选 4 指出发起页的草稿有两份：`approval-start` 的 module 拥有 `record.formData` / `record.ccUserIds`，却只给 `DeepReadonly` 的 view；`approval-start-browser` 只好镜像两个 `ref`，用两次 `JSON.stringify` 比较后经 `edit()` 写回，再用 `syncing` 标志避免自己的回灌触发 watch，锁定时的回灌也写在 facade 里，单测无法到达。「当前草稿是什么」因此要同时读 module、facade 和视图三个文件。现在 module 在每次打开时创建一个 `reactive` 的 `draft`（`formData` + `ccUserIds`），从 `record` 初始化并对外公开；module 内部装一个 `flush: 'sync'` 的深 watch——可编辑时把变化交给 `edit()` 落进 record 并持久化，不可编辑时从 record 整体回灌。`edit()` 仍是程序化写入口（提交时用它把设计器给的 values 落账）：当调用方不是 draft 本身时，它把接受后的值整体赋回 draft，由此触发的那次 watch 因内容与 record 相等而立即返回。

`ApprovalStartView` 删除 `formData` 与 `ccUserIds`，只保留 template、recipients、attachments、phase、locked、error 等读模型，草稿只有一个来源。`approval-start-browser` 退化为绑定 adapter：`formData` / `ccUserIds` 改为以 `page.draft` 为源的可写 computed，删除 `sync()`、`syncing`、两处 JSON 比较、深 watch 与 `page.edit` 调用；`rule` 的 `rulesJson` 镜保留，因为它是模板只读内容、不是草稿，换掉会让设计器重建，`option` 派生也不变。`apply.vue` 的 `v-model` 不用改。

回灌的边界保持现状：`phase !== 'editing'` 或 `loading` 时 draft 的变化被回灌为 record 内容，不提示；`editing` 下 `edit()` 因存储失败返回 false 时不回灌——保留用户输入、显示错误，`storageFailed` 继续挡住提交。回灌要连续写 `formData` 与 `ccUserIds` 两个字段，而 watch 是同步的，因此 module 用一个只在回灌期间为 true 的内部 `reflowing` 标志保证原子性；它不参与内容判断，也不是 facade 原来的 `syncing` 镜像守卫。打开释放时停掉 draft watch，释放后的改动不再写入，重新打开会从 record 重新建 draft。ADR 0008 的「待确认时内容保持确定」仍由 phase 与回灌规则保证，本次只把 enforcement 从 facade 搬回 module。

否决的替代方案：`bind()` 返回 get/set 对——form-create 会原地深改模型，facade 仍要包 `customRef` 或深 watch + 镜像，泄漏照旧；保留 view 里的草稿快照——「当前草稿」仍有两个答案；让 form-create 直接拥有模型、module 只观察——仍然需要 module 提供一个可写对象，且锁定时的回灌会落回视图层。

测试新增：深改 draft 被接受并持久化、`sending` / `unconfirmed` 下改动被回灌为提交内容、存储失败保留 draft 并报错、程序化 `edit()` 后 draft 同步、`release()` 后 draft 不再写入；现有 `page.edit(...)` 用例保留，只把 view 断言改为 draft。facade 无单测，现有 e2e 证明 `v-model` 接线，不新增浏览器用例。

Related: ADR 0008（待确认内容冻结）；ADR 0009（打开时捕获一次）；ADR 0016（请求作用域）。
