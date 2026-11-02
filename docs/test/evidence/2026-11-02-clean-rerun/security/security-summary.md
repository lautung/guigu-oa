# 安全定向用例结果（#113 决议）

- 执行时间：2026-11-02T04:17:23.182Z
- 被测版本：`abfddc24d0619c60e315b10e8fb2c1c0486dfbb3`（套件运行于提交前的工作区，内容与 `abfddc2` 一致；采集时 HEAD 仍为父提交 `8655107`）
- 入口：http://localhost:9530（API 经 `/dev-api`），匿名面 http://localhost:8800
- runId：`secmvyqsgdt`

| 用例 | 族 | 级别 | 结果 | 实际 |
|---|---|---|---|---|
| SEC-JWT-01 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-02 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-03 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-04 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-05 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-06 | SEC-JWT | S1 | 通过 | code=208 |
| SEC-JWT-07 | SEC-JWT | S3 | 通过 | code=208，登出后令牌失效 |
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
| SEC-UPLOAD-04 | SEC-UPLOAD | S2 | 通过 | code=200, attachmentId=8 |
| SEC-UPLOAD-05 | SEC-UPLOAD | S2 | 通过 | code=205（拒绝） |
| SEC-UPLOAD-06 | SEC-UPLOAD | S2 | 通过 | code=205（拒绝） |
| SEC-UPLOAD-07 | SEC-UPLOAD | S3 | 通过 | code=201, message=流程文件必须是 zip |
| SEC-UPLOAD-08 | SEC-UPLOAD | S2 | 通过 | {"code":200,"processDefinitionPath":"processes/sha256/112be2619e5af19c124c8b39170267478b1d8791349925e510da389e27b1b708.zip","processDefinitionKey":"sec-evil"} |
| SEC-UPLOAD-09 | SEC-UPLOAD | S2 | 通过 | {"putStatus":403,"completeCode":201,"message":"附件上传凭证已过期，请重新选择文件"} |
| SEC-CSRF-01 | SEC-CSRF | S4 | 通过 | 无 ACAO 头 |
| SEC-CSRF-02 | SEC-CSRF | S4 | 通过 | 无 ACAO 头 |
| SEC-CSRF-03 | SEC-CSRF | S4 | 通过 | {"status":401,"allowOrigin":null,"allowHeaders":null} |
| SEC-CSRF-04 | SEC-CSRF | S3 | 通过 | {"beforeStatus":1,"getCode":201,"afterStatus":1,"putCode":200,"writtenStatus":0} |
| SEC-CSRF-05 | SEC-CSRF | S3 | 通过 | {"publishMappings":["Post"],"updateStatusMappings":["Put"]} |
| SEC-BRUTE-01 | SEC-BRUTE | S3 | 通过 | {"missingCode":208,"wrongCode":208,"sameMessage":true} |
| SEC-BRUTE-02 | SEC-BRUTE | S3 | 通过 | {"failedLogins":30,"correctLoginCode":208} |
| SEC-LEAK-01 | SEC-LEAK | S2 | 通过 | 0/6 含 password |
| SEC-LEAK-02 | SEC-LEAK | S2 | 通过 | 无 password |
| SEC-LEAK-03 | SEC-LEAK | S4 | 通过 | 未暴露 |
| SEC-LEAK-04 | SEC-LEAK | S4 | 通过 | 未暴露 |
| SEC-LEAK-05 | SEC-LEAK | S4 | 通过 | {"status":401,"code":208} |
| SEC-LEAK-06 | SEC-LEAK | S3 | 通过 | 只含 id/name（5 个） |
| SEC-LEAK-07 | SEC-LEAK | S4 | 通过 | code=201, message=执行全局异常处理... |
| SEC-LEAK-08 | SEC-LEAK | S2 | 通过 | {"passwordHits":0,"jwtHits":0} |
| SEC-LEAK-09 | SEC-LEAK | S4 | 通过 | Server=nginx |
| SEC-HDR-01 | SEC-HDR | S3 | 通过 | 4/4 页带强制 CSP |
| SEC-HDR-02 | SEC-HDR | S3 | 通过 | 全部 DENY |
| SEC-HDR-03 | SEC-HDR | S4 | 通过 | 文档与静态资源都是 nosniff |
| SEC-HDR-04 | SEC-HDR | S3 | 通过 | connect-src 放行 http://localhost:9000 |
| SEC-HDR-05 | SEC-HDR | S3 | 通过 | Playwright：无违规 |
| SEC-PAGE-01 | SEC-PAGE | S3 | 通过 | code=201（拒绝） |
| SEC-PAGE-02 | SEC-PAGE | S3 | 通过 | code=201（拒绝） |
| SEC-PAGE-03 | SEC-PAGE | S4 | 通过 | {"negative":{"status":200,"code":201},"zero":{"status":200,"code":201}} |
| SEC-XSS-01 | SEC-XSS | S1 | 通过 | Playwright：未执行 |

合计 61 条：通过 61、失败 0、跳过 0。

