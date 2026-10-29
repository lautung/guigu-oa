# #136 复跑补充：响应形状与发起页选择

SEC-LEAK-06 只验证「普通账号拿到的每条抄送人选项只含 `id` 与 `name`」。这里在同一套 Compose 栈上补两件事：修复前后的响应差异，以及抄送人选择在发起页是否仍然可用。

## 环境

| 项 | 值 |
|---|---|
| 日期 | 2026-10-29T05:35Z（修复前）与 05:52Z（修复后） |
| 修复前版本 | `883ce8a47f4edededa90748f17d4851a56aa6579` |
| 修复后版本 | `adb9c92a11762bbd667350f22d4e22032e5e873c` |
| 入口 | `http://localhost:9530`（API 经 `/dev-api`） |
| 账号 | `user`（无配置菜单权限）、`admin`（配置管理员） |

## 修复前后响应

同一命令（`user` 登录后取 `token`，再请求抄送人选项）：

```sh
TOKEN=$(curl -sS -X POST http://localhost:9530/dev-api/admin/system/index/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user","password":"111111"}' | jq -r .data.token)
curl -sS http://localhost:9530/dev-api/admin/process/carbonCopyRecipients -H "token: $TOKEN"
```

| 版本 | code | 条数 | 每条字段 |
|---|---|---|---|
| `883ce8a`（修复前） | 200 | 7 | `id`, `username`, `name` |
| `adb9c92`（修复后） | 200 | 5 | `id`, `name` |

修复前样例（节选，登录名为真实值）：`{"id":5,"username":"carol","name":"卡萝"}`。修复后同样请求的第一条为 `{"id":5,"name":"卡萝"}`；`admin` 的响应同样只有 `id`/`name`，且不含自己（`id=1`）。

条数从 7 变为 5 与本次修改无关：修复前的 7 条里有三条是基线扫描遗留的 `sec-b-*` 临时账号（扫描器每次运行会创建一个临时账号，本轮复跑后已按临时数据处理）。

## 发起页仍可用

用 `user` 登录，打开「发起审批 → 演示请假申请」，展开「抄送给」：

- 下拉列出 4 个可选人（卡萝、管理员、艾丽丝、鲍勃），只显示姓名，当前用户不在列表里；
- 选中「鲍勃」后输入框显示姓名标签。

截图：`carbon-copy-picker.png`。客户端只读取 `id` 与 `name`（`apply.vue` 的 `label: name, value: id`），所以收窄响应没有改变选择与提交行为；提交路径由合同测试 `httpCarbonCopyMemberCanReadFullApprovalButCannotProcessIt` 以 `ccUserIds` 覆盖。

## 环境验收

复跑前在同一栈上执行 `./scripts/compose-baseline-check.sh`：**20 通过，0 失败**（清理基线扫描遗留的临时账号后）。

## 结论

- 普通账号不再拿到登录名，`username` 不再出现在该响应中；
- 抄送人选择按显示名工作，发起页行为不变；
- SEC-LEAK-06 由失败转为通过，判定口径收紧为「每条只含 `id` 与 `name`」。
