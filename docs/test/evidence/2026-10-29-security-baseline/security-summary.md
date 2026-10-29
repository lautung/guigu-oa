# 安全定向用例结果（#113 决议）

- 执行时间：2026-10-29T03:47:24.611Z
- 被测版本：`5a36d3eb475ecf0fd610e3fac7703bcbb9512a31`
- 入口：http://localhost:9530（API 经 `/dev-api`），匿名面 http://localhost:8800
- runId：`secmvszyjt0`

| 用例 | 族 | 级别 | 结果 | 实际 |
|---|---|---|---|---|
| SEC-JWT-01 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-02 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-03 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-04 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-05 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-06 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-07 | SEC-JWT | S3 | **失败** | 登出 code=200，重放仍 code=200 |
| SEC-JWT-08 | SEC-JWT | S2 | 通过 | code=209 |
| SEC-JWT-09 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-10 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-IDOR-01 | SEC-IDOR | S1 | 通过 | code=403 |
| SEC-IDOR-02 | SEC-IDOR | S1 | 通过 | code=403 |
| SEC-IDOR-03 | SEC-IDOR | S1 | 通过 | code=205（拒绝） |
| SEC-IDOR-04 | SEC-IDOR | S2 | 通过 | code=205（拒绝） |
| SEC-IDOR-05 | SEC-IDOR | S2 | 通过 | code=200, records=0 |
| SEC-IDOR-06 | SEC-IDOR | S2 | 通过 | code=205（拒绝） |
| SEC-IDOR-07 | SEC-IDOR | S2 | 通过 | code=200, data=null |
| SEC-IDOR-08 | SEC-IDOR | S1 | 通过 | code=403 |
| SEC-IDOR-09 | SEC-IDOR | S2 | 通过 | code=205（拒绝） |
| SEC-MEM-01 | SEC-MEM | S2 | 通过 | {"isApprove":false,"canUpload":true,"approveCode":403} |
| SEC-MEM-02 | SEC-MEM | S2 | 通过 | {"isApprove":true,"canUpload":true} |
| SEC-MEM-03 | SEC-MEM | S2 | 通过 | {"isApprove":false,"canUpload":false} |
| SEC-MEM-04 | SEC-MEM | S2 | 通过 | {"approveCode":200,"isApprove":false,"canUpload":false,"replayCode":403} |
| SEC-MEM-05 | SEC-MEM | S1 | 通过 | {"showCode":403,"attachmentsCode":205,"uploadCode":205} |
| SEC-GATE-01 | SEC-GATE | S1 | 通过 | 35/35 端点 code=205 |
| SEC-GATE-02 | SEC-GATE | S1 | 通过 | code=205 |
| SEC-GATE-03 | SEC-GATE | S2 | 通过 | 6/6 code=200 |
| SEC-UPLOAD-01 | SEC-UPLOAD | S3 | 通过 | code=201, message=附件超过大小限制 |
| SEC-UPLOAD-02 | SEC-UPLOAD | S3 | 通过 | {"putStatus":403,"completeCode":201} |
| SEC-UPLOAD-03 | SEC-UPLOAD | S2 | 通过 | code=201, message=附件内容与登记信息不一致 |
| SEC-UPLOAD-04 | SEC-UPLOAD | S2 | 通过 | code=200, attachmentId=12 |
| SEC-UPLOAD-05 | SEC-UPLOAD | S2 | 通过 | code=205（拒绝） |
| SEC-UPLOAD-06 | SEC-UPLOAD | S2 | 通过 | code=205（拒绝） |
| SEC-UPLOAD-07 | SEC-UPLOAD | S3 | 通过 | code=201, message=流程文件必须是 zip |
| SEC-UPLOAD-08 | SEC-UPLOAD | S2 | 通过 | {"code":200,"processDefinitionPath":"processes/sha256/112be2619e5af19c124c8b39170267478b1d8791349925e510da389e27b1b708.zip","processDefinitionKey":"sec-evil"} |
| SEC-UPLOAD-09 | SEC-UPLOAD | S2 | 通过 | {"putStatus":403,"completeCode":201,"message":"附件上传凭证已过期，请重新选择文件"} |
| SEC-CSRF-01 | SEC-CSRF | S4 | 通过 | 无 ACAO 头 |
| SEC-CSRF-02 | SEC-CSRF | S4 | **失败** | ACAO=* |
| SEC-CSRF-03 | SEC-CSRF | S4 | **失败** | {"status":200,"allowOrigin":"*","allowHeaders":"token, content-type"} |
| SEC-CSRF-04 | SEC-CSRF | S3 | **失败** | {"beforeStatus":1,"toggleCode":200,"afterStatus":0} |
| SEC-CSRF-05 | SEC-CSRF | S3 | **失败** | publish 由 GET 触发 |
| SEC-BRUTE-01 | SEC-BRUTE | S3 | 通过 | {"missingCode":208,"wrongCode":208,"sameMessage":true} |
| SEC-BRUTE-02 | SEC-BRUTE | S3 | **失败** | {"failedLogins":30,"correctLoginCode":200} |
| SEC-LEAK-01 | SEC-LEAK | S2 | **失败** | 6/6 条含 password |
| SEC-LEAK-02 | SEC-LEAK | S2 | **失败** | data.password 存在 |
| SEC-LEAK-03 | SEC-LEAK | S4 | **失败** | 匿名返回 openapi 文档 |
| SEC-LEAK-04 | SEC-LEAK | S4 | **失败** | 匿名可读 |
| SEC-LEAK-05 | SEC-LEAK | S4 | 通过 | {"status":200,"code":208} |
| SEC-LEAK-06 | SEC-LEAK | S3 | **失败** | 返回 5 个启用用户 |
| SEC-LEAK-07 | SEC-LEAK | S4 | 通过 | code=201, message=执行全局异常处理... |
| SEC-LEAK-08 | SEC-LEAK | S2 | 通过 | {"passwordHits":0,"jwtHits":0} |
| SEC-LEAK-09 | SEC-LEAK | S4 | **失败** | Server=nginx/1.31.8 |
| SEC-PAGE-01 | SEC-PAGE | S3 | **失败** | limit=100000 被接受，返回 6 条 |
| SEC-PAGE-02 | SEC-PAGE | S3 | **失败** | limit=100000 被接受，返回 31 条 |
| SEC-PAGE-03 | SEC-PAGE | S4 | 通过 | {"negative":{"status":200,"code":200},"zero":{"status":200,"code":200}} |
| SEC-XSS-01 | SEC-XSS | S1 | **失败** | Playwright：脚本执行（已留证） |

合计 56 条：通过 41、失败 15、跳过 0。

## 失败项（候选缺陷）

- **SEC-JWT-07**（S3）登出后 access token 仍可访问（令牌未吊销）：登出 code=200，重放仍 code=200
- **SEC-CSRF-02**（S4）审批接口不回显恶意 Origin 的 CORS 头：ACAO=*
- **SEC-CSRF-03**（S4）跨源预检不授予带 token 头的写请求：{"status":200,"allowOrigin":"*","allowHeaders":"token, content-type"}
  - 证据：`{"status":200,"allowOrigin":"*","allowHeaders":"token, content-type"}`
- **SEC-CSRF-04**（S3）GET updateStatus 产生写副作用：{"beforeStatus":1,"toggleCode":200,"afterStatus":0}
  - 证据：`{"beforeStatus":1,"toggleCode":200,"afterStatus":0}`
- **SEC-CSRF-05**（S3）GET publish 是写操作（源码事实）：publish 由 GET 触发
- **SEC-BRUTE-02**（S3）30 次失败后正确密码仍可登录（无限流/锁定）：{"failedLogins":30,"correctLoginCode":200}
  - 证据：`{"failedLogins":30,"correctLoginCode":200}`
- **SEC-LEAK-01**（S2）用户分页不返回密码哈希：6/6 条含 password
- **SEC-LEAK-02**（S2）用户详情不返回密码哈希：data.password 存在
- **SEC-LEAK-03**（S4）OpenAPI 文档匿名可读：匿名返回 openapi 文档
- **SEC-LEAK-04**（S4）Swagger UI 匿名可读：匿名可读
- **SEC-LEAK-06**（S3）抄送候选人接口向普通用户暴露全部启用用户：返回 5 个启用用户
- **SEC-LEAK-09**（S4）客户端响应头暴露 nginx 版本：Server=nginx/1.31.8
- **SEC-PAGE-01**（S3）用户分页接受无上限 limit：limit=100000 被接受，返回 6 条
- **SEC-PAGE-02**（S3）审批待处理分页接受无上限 limit：limit=100000 被接受，返回 31 条
- **SEC-XSS-01**（S1）审批表单字段存储型 XSS 不执行：Playwright：脚本执行（已留证）

## 重建说明

本文件在 #139 修复轮被一次 --only SEC-HDR 的扫描误覆盖后重建：id/family/severity/title/expected 取自基线当轮的 security-scan.mjs，actual/pass/skipped 取自原样保留的控制台输出（security-console.txt）。durationMs 与未进入 actual 的 evidence 无法恢复，分别为 null / 只保留能由 actual 解析出的对象。
