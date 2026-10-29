# 安全定向用例结果（#113 决议）

- 执行时间：2026-10-29T12:33:08.616Z
- 被测版本：`cddd90dca734f416cb427de1511e35e36475c7ba`
- 入口：http://localhost:9530（API 经 `/dev-api`），匿名面 http://localhost:8800
- runId：`secmvtixoqd`

| 用例 | 族 | 级别 | 结果 | 实际 |
|---|---|---|---|---|
| SEC-CSRF-01 | SEC-CSRF | S4 | 通过 | 无 ACAO 头 |
| SEC-CSRF-02 | SEC-CSRF | S4 | 通过 | 无 ACAO 头 |
| SEC-CSRF-03 | SEC-CSRF | S4 | 通过 | {"status":200,"allowOrigin":null,"allowHeaders":null} |
| SEC-CSRF-04 | SEC-CSRF | S3 | 通过 | {"beforeStatus":1,"getCode":201,"afterStatus":1,"putCode":200,"writtenStatus":0} |
| SEC-CSRF-05 | SEC-CSRF | S3 | 通过 | {"publishMappings":["Post"],"updateStatusMappings":["Put"]} |

合计 5 条：通过 5、失败 0、跳过 0。

