# 审批接口跨源人工复核（#141）

- 被测版本：`cddd90d`（`fix: stop the approval API from answering any origin with CORS (#141)`）
- 栈：本机 Compose 演示栈（`SPRING_PROFILES_ACTIVE=dev,demo`，5 容器 healthy）
- 入口：`http://localhost:9530`（API 经 `/dev-api`），匿名面 `http://localhost:8800`
- 定向结果：本目录 `security-results.json`（SEC-CSRF 5/5 通过）

## 命令

```bash
TOKEN=$(curl -s -X POST http://localhost:9530/dev-api/admin/system/index/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"111111"}' | jq -r .data.token)

curl -sS -D - -o /dev/null -H "token: $TOKEN" -H 'Origin: http://evil.example' \
  http://localhost:9530/dev-api/admin/process/findPending/1/10

curl -sS -D - -o /dev/null -X OPTIONS \
  -H 'Origin: http://evil.example' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: token,content-type' \
  http://localhost:9530/dev-api/admin/process/approve
```

## 修复前后

| 请求 | 基线轮 | 本轮（`cddd90d`） |
|---|---|---|
| `GET .../findPending/1/10`，`Origin: http://evil.example` | `Access-Control-Allow-Origin: *`（SEC-CSRF-02 `ACAO=*`） | 200，正文 `code=200`，无 `Access-Control-*` 头 |
| `OPTIONS .../approve`，无 token | 200，`ACAO: *`，`Access-Control-Allow-Headers: token, content-type`（SEC-CSRF-03） | 200，正文 `{"code":208,"message":"认证失败"}`，无 `Access-Control-*` 头 |
| `OPTIONS .../approve`，带 token | MVC 预检返回 `ACAO: *`、`Allow-Headers: token, content-type`、`Max-Age: 1800` | 200，`Allow: GET, HEAD, POST, PUT, DELETE, OPTIONS, TRACE, PATCH`，无 `Access-Control-*` 头 |

修复后 9530 的 GET 响应头（无 `Access-Control-*`）：

```
HTTP/1.1 200
Server: nginx
X-Trace-Id: cc395029-eb1d-4172-b87e-451eec7b88bf
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Content-Type: application/json
Content-Length: 6077
```

修复后 9530 的预检响应头（无 `Access-Control-*`）：

```
HTTP/1.1 200
Server: nginx
Allow: GET, HEAD, POST, PUT, DELETE, OPTIONS, TRACE, PATCH
X-Trace-Id: 5a3381a0-a1c9-4224-ab60-ff5c2308535e
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Content-Length: 0
```

不带 token 的预检先被 `TokenAuthenticationFilter` 挡下（`code=208`）：去掉 Spring Security 的
`.cors()` 之后，服务端不再替浏览器处理预检，浏览器不会发出任何带凭证的跨源请求。

## 守卫

| 层 | 守卫 | 结果 |
|---|---|---|
| 服务端 JUnit | `CorsContractTest`：恶意 Origin 无 ACAO；预检不授予 token 头；无控制器或全局跨源配置 | 3/3 通过 |
| 定向扫描 | `SEC-CSRF-01..05` | 5/5 通过 |
| 客户端 | `npm run verify` | 487 单测通过 |
| 真实栈 e2e | `npm run test:e2e:stack` | 26/26 通过（1.1m） |

## 红绿验证

把 `@CrossOrigin` 临时加回 `ProcessController` 后：

- `approvalReadDoesNotAnswerAHostileOriginWithCorsHeaders` 失败：
  `Response should not contain header 'Access-Control-Allow-Origin'`；
- `crossOriginPreflightDoesNotGrantTheTokenHeader` 失败：预检响应带
  `Access-Control-Allow-Origin: *` 与 `Access-Control-Allow-Headers: token, content-type`；
- `noControllerOrGlobalConfigurationEnablesCrossOrigin` 失败，列出 `ProcessController#...`
  的全部处理器。

移除后 3/3 通过。说明这三条断言确实钉住了缺陷，不是恒真测试。
