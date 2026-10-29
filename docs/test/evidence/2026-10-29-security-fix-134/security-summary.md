# 安全定向用例结果（#113 决议）

- 执行时间：2026-10-29T04:51:44.825Z
- 被测版本：`6349d8026d5f2d134ea39249e4d09e11d88d9485`
- 入口：http://localhost:9530（API 经 `/dev-api`），匿名面 http://localhost:8800
- runId：`secmvt2gcd0`

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

合计 10 条：通过 10、失败 0、跳过 0。

