# 安全定向用例结果（#113 决议）

- 执行时间：2026-10-29T05:27:08.113Z
- 被测版本：`6144f7ed2813794339326f6ea8fbd6b896219dbf`
- 入口：http://localhost:9530（API 经 `/dev-api`），匿名面 http://localhost:8800
- runId：`secmvt3ptd4`

| 用例 | 族 | 级别 | 结果 | 实际 |
|---|---|---|---|---|
| SEC-BRUTE-01 | SEC-BRUTE | S3 | 通过 | {"missingCode":208,"wrongCode":208,"sameMessage":true} |
| SEC-BRUTE-02 | SEC-BRUTE | S3 | 通过 | {"failedLogins":30,"correctLoginCode":208} |

合计 2 条：通过 2、失败 0、跳过 0。

