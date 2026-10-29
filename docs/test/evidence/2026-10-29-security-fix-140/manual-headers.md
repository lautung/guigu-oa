# 匿名暴露面人工复核（#140）

- 被测版本：`80edbc2`（`fix: keep API docs and the nginx version off the anonymous surface (#140)`）
- 栈：本机 Compose 演示栈（`SPRING_PROFILES_ACTIVE=dev,demo`，5 容器 healthy）
- 入口：`http://localhost:9530`（API 经 `/dev-api`），匿名面 `http://localhost:8800`
- 定向结果：本目录 `security-results.json`（SEC-LEAK-03/04/09 3/3 通过）

## 命令

```bash
curl -sS -D - http://localhost:8800/v3/api-docs
curl -sS -D - http://localhost:8800/swagger-ui/index.html
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' http://localhost:8800/swagger-ui.html
curl -sSI http://localhost:9530/
```

## 修复前后

| 目标 | 修复前（基线轮） | 修复后 |
|---|---|---|
| `8800 /v3/api-docs` | HTTP 200，29 311 字节 OpenAPI 3.1.0 文档，55 条路径、25 个 schema | HTTP 200，64 字节通用错误体 `{"code":201,"data":null,"message":"执行全局异常处理..."}`，无 `openapi` 字段 |
| `8800 /swagger-ui/index.html` | HTTP 200，Swagger UI 页面 | 同一个 64 字节错误体，不再返回页面 |
| `8800 /swagger-ui.html` | 302 跳转进 Swagger UI | 不再跳转，返回同一个错误体 |
| `9530` 的 `Server` 头 | `nginx/1.31.8` | `nginx` |

修复后 8800 的响应头（`/v3/api-docs`）：

```
HTTP/1.1 200
X-Trace-Id: 9b959557-4829-4228-9ba1-8276433e628a
X-Content-Type-Options: nosniff
X-Xss-Protection: 0
X-Frame-Options: DENY
Content-Type: application/json
Content-Length: 64
```

9530 的响应头保持不变（#139 的三条仍在）：

```
HTTP/1.1 200 OK
Server: nginx
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';
  img-src 'self' data: blob: https://gw.alicdn.com; font-src 'self' data:;
  connect-src 'self' http://localhost:9000; object-src 'none'; base-uri 'self';
  form-action 'self'; frame-ancestors 'none'
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
```

关闭后这两个路径返回的是项目全局异常处理器的通用错误体（与 SEC-LEAK-07 看到的一致），
不是 OpenAPI 文档或 Swagger UI 页面——匿名访问者拿不到端点、参数与数据模型。

## profile 契约

关闭开关按「默认关、dev 显式开」布置，`OpenApiExposureConfigTest` 按加载顺序合并
profile YAML 后断言两条生效路径（5 个参数化用例）：

| profile 组合 | api-docs / swagger-ui |
|---|---|
| 无 profile 文件（只剩 `application.yml`） | 关闭 |
| `dev` | 打开 |
| `dev,demo`（Compose 演示栈） | 关闭 |
| `prod` | 关闭 |
| `dev,prod` | 关闭 |

把 `application-demo.yml` 的 `springdoc` 段删掉后该测试立刻变红
（`expected: <false> but was: <true>`），说明 demo 的覆盖是必须的，不是冗余配置。

profile 是后加载者覆盖前者：`prod` 只有在列表末尾时才算最后声明，写成 `prod,dev` 会让 dev
的开启重新生效——`application-prod.yml` 的注释写明了这一点，测试也只声明 `dev,prod` 这一种
会出现的顺序，不假装已按任意顺序兜底。

## 回归层

| 层 | 守卫 | 结果 |
|---|---|---|
| 服务端 JUnit | `OpenApiExposureConfigTest` | 5/5 通过 |
| 演示栈基线 | `./scripts/compose-baseline-check.sh` 新增「匿名暴露面」3 条 | 26 通过，0 失败 |
| 定向扫描 | `SEC-LEAK-03/04/09` | 3/3 通过 |
| 浏览器 | `tests/security/security-server-version.spec.ts` | 1/1 通过 |
| 真实栈 e2e | `npm run test:e2e:stack` | 26/26 通过（1.1m） |

`compose-baseline-check.sh` 的三条新断言不会把「请求失败」当成安全：后端不可达
（`HTTP 000`/空）记为失败，`/v3/api-docs` 只有不带 `openapi` 才算通过，
`/swagger-ui/index.html` 出现 5xx 也记为失败。

## ZAP 被动 baseline 前后对比

三轮都是同一被动脚本（`node scripts/zap-baseline.mjs`，无 spider、无主动扫描），目标数从
基线轮的 14 个增加到 28 个（#139 修好了静态资源探测，`/static/*` 真正被扫到）。

| 告警 | 基线轮（14 目标） | #139 轮（28 目标） | 本轮（28 目标） |
|---|---|---|---|
| Server Leaks Version Information（Low） | 7 | 21 | **0** |
| Content Security Policy Header Not Set（Medium） | 6 | 1（剩 `8800/swagger-ui`） | **0** |
| Missing Anti-clickjacking Header（Medium） | 5 | 0 | 0 |
| X-Content-Type-Options Header Missing（Low） | 5 | 0 | 0 |
| CSP: style-src unsafe-inline（Medium） | 0 | 5 | 5（ADR 0026 已接受） |
| Modern Web Application（Informational） | 6 | 6 | 5 |
| Suspicious Comments（Informational） | 0 | 6 | 6（压缩产物误报） |
| 合计 | 29 | 39 | 16 |

`Server Leaks Version Information` 与 `swagger-ui` 的 CSP 两条都消失：前者是 `server_tokens off`
（`Server: nginx`），后者是文档关闭后该页面不再存在。剩下 5 条 Medium 全是
`style-src 'unsafe-inline'`，属 ADR 0026 已记录的 Element Plus 内联样式放宽；
11 条 Informational 是扫描器对 SPA 与压缩 bundle 的常规观察，不阻断。

完整 ZAP HTML/JSON 报告体积大，不入仓：
`/tmp/security-evidence-full/2026-10-29-security-fix-140/zap-full/zap-report.html`。


