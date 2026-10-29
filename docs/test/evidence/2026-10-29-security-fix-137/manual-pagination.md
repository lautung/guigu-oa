# 分页上限人工复核（#137）

- 被测版本：`61f91a5e267df934c544c5fb5f1865dc77fdf04a`
- 栈：本机 Compose 演示栈（`docker compose up -d --build --wait`，5 容器 healthy）
- 入口：`http://localhost:9530/dev-api`
- 账号：`admin` / `bob`，密码 `111111`

## 命令

```bash
ADMIN=$(curl -s -X POST http://localhost:9530/dev-api/admin/system/index/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"111111"}' | jq -r '.data.token')
BOB=$(curl -s -X POST http://localhost:9530/dev-api/admin/system/index/login \
  -H 'Content-Type: application/json' -d '{"username":"bob","password":"111111"}' | jq -r '.data.token')

curl -s "http://localhost:9530/dev-api/admin/system/sysUser/1/10" -H "token: $ADMIN"
curl -s "http://localhost:9530/dev-api/admin/system/sysUser/1/500" -H "token: $ADMIN"
curl -s "http://localhost:9530/dev-api/admin/system/sysUser/1/0" -H "token: $ADMIN"
curl -s "http://localhost:9530/dev-api/admin/system/sysUser/-1/10" -H "token: $ADMIN"
curl -s "http://localhost:9530/dev-api/admin/process/findPending/1/10" -H "token: $BOB"
curl -s "http://localhost:9530/dev-api/admin/process/findPending/1/100000" -H "token: $BOB"
curl -s "http://localhost:9530/dev-api/admin/process/findStarted/1/501" -H "token: $BOB"
```

## 结果

| 账号 | 路径 | HTTP | body.code | data.size | records | message |
|---|---|---|---|---|---|---|
| admin | `/admin/system/sysUser/1/10` | 200 | 200 | 10 | 6 | 成功 |
| admin | `/admin/system/sysUser/1/500` | 200 | 200 | 500 | 6 | 成功 |
| admin | `/admin/system/sysUser/1/0` | 200 | 201 | - | - | 每页记录数必须在 1 到 500 之间 |
| admin | `/admin/system/sysUser/-1/10` | 200 | 201 | - | - | 页码必须大于等于 1 |
| bob | `/admin/process/findPending/1/10` | 200 | 200 | 10 | 10 | 成功 |
| bob | `/admin/process/findPending/1/100000` | 200 | 201 | - | - | 每页记录数必须在 1 到 500 之间 |
| bob | `/admin/process/findStarted/1/501` | 200 | 201 | - | - | 每页记录数必须在 1 到 500 之间 |

## 结论

- 复现用的 `limit=100000` 在用户分页与待处理分页都返回 `code=201`，不再返回记录。
- `limit=500` 与正常页大小仍返回 `code=200`，`data.size` 分别回显 500 与 10；办事列表内容不受影响。
- 页码 0 与 -1 返回 `code=201`（页码消息），HTTP 状态仍是 200，沿用现有 `Result` 信封，不产生 5xx。
- 拒绝时 `data` 为空；边界值提示与 `PageWindow` 的单元测试一致。

本轮只复跑与 #137 相关的 SEC-PAGE 族；其他安全族在各自缺陷的修复轮复跑。
