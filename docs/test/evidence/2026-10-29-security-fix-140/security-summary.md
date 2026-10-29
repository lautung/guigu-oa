# 安全定向用例结果（#113 决议）

- 执行时间：2026-10-29T10:52:04.647Z
- 被测版本：`80edbc2dbc1db07149ac2cbd186930bff7068b13`
- 入口：http://localhost:9530（API 经 `/dev-api`），匿名面 http://localhost:8800
- runId：`secmvtfbpup`

| 用例 | 族 | 级别 | 结果 | 实际 |
|---|---|---|---|---|
| SEC-LEAK-03 | SEC-LEAK | S4 | 通过 | 未暴露 |
| SEC-LEAK-04 | SEC-LEAK | S4 | 通过 | 未暴露 |
| SEC-LEAK-09 | SEC-LEAK | S4 | 通过 | Server=nginx |

合计 3 条：通过 3、失败 0、跳过 0。

