# #135 复跑补充：锁定、恢复与审计

SEC-BRUTE 只验证「30 次快速失败后正确密码被拒」。这里在同一套 Compose 栈上补三件事：账号到 10 次失败后是否真的进入 15 分钟锁定、其他账号是否不受牵连、锁定/解锁是否写审计。

## 环境

| 项 | 值 |
|---|---|
| 日期 | 2026-10-29T05:27Z 起 |
| 被测版本 | `6144f7ed2813794339326f6ea8fbd6b896219dbf` |
| 入口 | `http://localhost:8800` 匿名面（与扫描器相同的来源地址） |
| runId | `sec-lock-mvt3q3vb`（临时账号，结束后删除） |

## 脚本

`security-scan.mjs` 的 30 次失败是连发的；第 6 次起会被指数等待挡下，所以计数停在 5，不会走到 10 次锁定。这里按策略要求逐次等待后再失败，走到第 10 次：

```sh
node --input-type=module -e '
const API = "http://localhost:8800"
async function call(method, path, { token, body } = {}) {
  const headers = {}
  if (token) headers.token = token
  if (body) headers["Content-Type"] = "application/json"
  const res = await fetch(API + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  return { status: res.status, json: await res.json() }
}
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const runId = Date.now().toString(36)
const username = `sec-lock-${runId}`
const password = "111111"

const admin = (await call("post", "/admin/system/index/login", { body: { username: "admin", password } })).json.data.token
await call("post", "/admin/system/sysUser/save", { token: admin, body: { username, password, name: "锁定验证账号", status: 1 } })
const page = await call("get", `/admin/system/sysUser/1/50?keyword=${encodeURIComponent(username)}`, { token: admin })
const userId = page.json.data.records[0].id

const codes = []
for (let attempt = 0; attempt < 5; attempt += 1) {
  codes.push((await call("post", "/admin/system/index/login", { body: { username, password: `wrong-${attempt}` } })).json.code)
}
for (const waitSeconds of [1, 2, 4, 8, 16]) {
  await sleep(waitSeconds * 1000)
  codes.push((await call("post", "/admin/system/index/login", { body: { username, password: "wrong-again" } })).json.code)
}
const locked = await call("post", "/admin/system/index/login", { body: { username, password } })
const other = await call("post", "/admin/system/index/login", { body: { username: "alice", password } })
await call("delete", `/admin/system/sysUser/remove/${userId}`, { token: admin })

console.log(JSON.stringify({
  username,
  failureCodes: codes,
  correctAfterTen: { code: locked.json.code, message: locked.json.message },
  otherAccountFromSameSource: other.json.code,
}, null, 2))
'
```

## 结果

| 检查 | 期望 | 实际 |
|---|---|---|
| 前 5 次错误密码 | `code=208` | 5×`208` |
| 等待 1/2/4/8/16 秒后的第 6–10 次错误密码 | `code=208` | 5×`208` |
| 第 10 次失败后的正确密码 | 被拒，`code=208` | `208`，`登录尝试过于频繁，请稍后再试` |
| 同一来源的另一个账号（`alice`）正确密码 | 可登录，`code=200` | `200` |

Redis 状态（锁定当时）：

| 键 | 值 | 说明 |
|---|---|---|
| `login:attempt:account:sec-lock-mvt3q3vb:failures` | `10` | 账号维度计数 |
| `login:attempt:account:sec-lock-mvt3q3vb:lock` | `1793252570802`（`2026-10-29T05:42:50.802Z`） | 锁定到期时间 |
| `login:attempt:account:sec-lock-mvt3q3vb:lock` TTL | `1189s` | 15 分钟锁定 + 5 分钟标记保留 |
| `login:attempt:source:192.168.65.1:failures` | `17`（扫描 7 + 本次 10） | 来源维度计数，未到 100 阈值 |

## 审计

```
login audit event=failed username=sec-lock-mvt3q3vb source=192.168.65.1 failures=10
login audit event=locked username=sec-lock-mvt3q3vb source=192.168.65.1 scope=account lockedUntil=2026-10-29T05:42:50.802542885Z
```

解锁分支没有等待 15 分钟真实到期：把锁定标记的值改成已过去的时间（`SET ... <past-epoch-ms> KEEPTTL`）模拟到期，再重建同一账号并用正确密码登录：

| 检查 | 期望 | 实际 |
|---|---|---|
| 锁定到期后的正确密码 | `code=200` 且签发 token | `200`，有 token |
| 解锁审计 | `event=unlocked` | `login audit event=unlocked username=sec-lock-mvt3q3vb source=192.168.65.1 scope=account` |

到期判定本身由 `LoginAuthContractTest.repeatedFailuresLockTheAccountEvenWhenTheCorrectPasswordArrivesLater` 用可控时钟确定性地覆盖（含解锁后计数从零开始）。

## 结论

账号维度按预期在 10 次失败后锁 15 分钟，正确密码在锁定期内被拒；同一来源的其他账号不受影响；失败、锁定、解锁都有审计。临时账号与锁定键在验证后删除/清空，来源计数随 15 分钟窗口过期。
