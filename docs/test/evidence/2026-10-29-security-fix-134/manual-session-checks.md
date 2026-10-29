# #134 复跑补充：refresh 与多客户端

SEC-JWT-07 只重放 access token。这里在同一套 Compose 栈（`6349d80`）上补两条：
登出后 refresh token 是否还能换新 access token；另一次登录是否被误伤。

## 脚本

```sh
# 直接用 8800 匿名面，避免经 /dev-api 的差异
node --input-type=module -e '
const API = "http://localhost:8800"
async function call(method, path, { token, body } = {}) {
  const headers = {}
  if (token) headers.token = token
  if (body) headers["Content-Type"] = "application/json"
  const res = await fetch(API + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  return { status: res.status, json: await res.json() }
}
const login = async () =>
  (await call("post", "/admin/system/index/login", { body: { username: "alice", password: "111111" } })).json.data

// 1. 同一次登录：登出后 access 与 refresh 都失效
const a = await login()
await call("post", "/admin/system/index/logout", { token: a.token, body: { refreshToken: a.refreshToken } })
console.log("access", (await call("get", "/admin/system/index/info", { token: a.token })).json.code)
console.log("refresh", (await call("post", "/admin/system/index/refresh", { body: { refreshToken: a.refreshToken } })).json.code)

// 2. 两次登录：退出 A 不影响 B
const first = await login()
const second = await login()
await call("post", "/admin/system/index/logout", { token: first.token, body: { refreshToken: first.refreshToken } })
console.log("A", (await call("get", "/admin/system/index/info", { token: first.token })).json.code)
console.log("B info", (await call("get", "/admin/system/index/info", { token: second.token })).json.code)
console.log("B refresh", (await call("post", "/admin/system/index/refresh", { body: { refreshToken: second.refreshToken } })).json.code)
'
```

## 结果

| 检查 | 期望 | 实际 |
|---|---|---|
| 登出调用 | `code=200` | `200` |
| 登出后同一 access token 重放 | `code=208` | `208` |
| 登出后同一 refresh token 换新令牌 | `code=208` | `208` |
| 被退出那次登录的 access 重放 | `code=208` | `208` |
| 另一次登录的 access 读 `/info` | `code=200` | `200` |
| 另一次登录的 refresh 换新令牌 | `code=200` | `200` |

结论：退出只作废这台客户端手里的那一对凭证，其他客户端仍能认出这个用户（ADR 0022）。
