# 安全定向用例结果（#113 决议）

- 执行时间：2026-10-29T08:39:46.074Z
- 被测版本：`09799e995c59f6a188cb9e3acab2a1df6db22f83`
- 入口：http://localhost:9530（API 经 `/dev-api`），匿名面 http://localhost:8800
- runId：`secmvtalkml`

| 用例 | 族 | 级别 | 结果 | 实际 |
|---|---|---|---|---|
| SEC-JWT-08 | SEC-JWT | S2 | 通过 | code=209 |
| SEC-GATE-01 | SEC-GATE | S1 | 通过 | 35/35 端点 code=205 |
| SEC-CSRF-01 | SEC-CSRF | S4 | 通过 | 无 ACAO 头 |
| SEC-CSRF-02 | SEC-CSRF | S4 | **失败** | ACAO=* |
| SEC-CSRF-03 | SEC-CSRF | S4 | **失败** | {"status":200,"allowOrigin":"*","allowHeaders":"token, content-type"} |
| SEC-CSRF-04 | SEC-CSRF | S3 | 通过 | {"beforeStatus":1,"getCode":201,"afterStatus":1,"putCode":200,"writtenStatus":0} |
| SEC-CSRF-05 | SEC-CSRF | S3 | 通过 | {"publishMappings":["Post"],"updateStatusMappings":["Put"]} |

合计 7 条：通过 5、失败 2、跳过 0。

## 失败项（候选缺陷）

- **SEC-CSRF-02**（S4）审批接口不回显恶意 Origin 的 CORS 头：ACAO=*
  - 证据：`{"allowOrigin":"*"}`
- **SEC-CSRF-03**（S4）跨源预检不授予带 token 头的写请求：{"status":200,"allowOrigin":"*","allowHeaders":"token, content-type"}
  - 证据：`{"status":200,"allowOrigin":"*","allowHeaders":"token, content-type"}`

