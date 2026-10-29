# 安全定向用例结果（#113 决议）

- 执行时间：2026-10-29T08:07:47.031Z
- 被测版本：`61f91a5e267df934c544c5fb5f1865dc77fdf04a`
- 入口：http://localhost:9530（API 经 `/dev-api`），匿名面 http://localhost:8800
- runId：`secmvt9gfca`

| 用例 | 族 | 级别 | 结果 | 实际 |
|---|---|---|---|---|
| SEC-PAGE-01 | SEC-PAGE | S3 | 通过 | {"code":201,"size":null,"records":null,"total":null,"ms":18} |
| SEC-PAGE-02 | SEC-PAGE | S3 | 通过 | {"code":201,"size":null,"records":null,"total":null,"ms":11} |
| SEC-PAGE-03 | SEC-PAGE | S4 | 通过 | {"negative":{"status":200,"code":201},"zero":{"status":200,"code":201}} |

合计 3 条：通过 3、失败 0、跳过 0。

