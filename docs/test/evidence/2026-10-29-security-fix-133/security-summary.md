# 安全定向用例结果（#113 决议）

- 执行时间：2026-10-29T04:16:50.507Z
- 被测版本：`ee27a7d5b797fc2f59facd9cca81acb516c9de17`
- 入口：http://localhost:9530（API 经 `/dev-api`），匿名面 http://localhost:8800
- runId：`secmvt17fpl`

| 用例 | 族 | 级别 | 结果 | 实际 |
|---|---|---|---|---|
| SEC-LEAK-01 | SEC-LEAK | S2 | 通过 | 0/6 含 password |
| SEC-LEAK-02 | SEC-LEAK | S2 | 通过 | 无 password |

合计 2 条：通过 2、失败 0、跳过 0。

