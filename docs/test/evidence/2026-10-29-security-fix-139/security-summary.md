# 安全定向用例结果（#113 决议）

- 执行时间：2026-10-29T09:42:47.144Z
- 被测版本：`0e9fef51622c6fb269c29b3b9ecc8368f32b6b8b`
- 入口：http://localhost:9530（API 经 `/dev-api`），匿名面 http://localhost:8800
- runId：`secmvtcuhwr`

| 用例 | 族 | 级别 | 结果 | 实际 |
|---|---|---|---|---|
| SEC-HDR-01 | SEC-HDR | S3 | 通过 | 4/4 页带强制 CSP |
| SEC-HDR-02 | SEC-HDR | S3 | 通过 | 全部 DENY |
| SEC-HDR-03 | SEC-HDR | S4 | 通过 | 文档与静态资源都是 nosniff |
| SEC-HDR-04 | SEC-HDR | S3 | 通过 | connect-src 放行 http://localhost:9000 |
| SEC-HDR-05 | SEC-HDR | S3 | 通过 | Playwright：无违规 |

合计 5 条：通过 5、失败 0、跳过 0。

