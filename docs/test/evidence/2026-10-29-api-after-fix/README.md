# 2026-10-29-api-after-fix（#127 · #145/#146 修复回归）

接口套件 after-fix 轮证据。执行记录：[`docs/test/execution/2026-10-30-api-after-fix.md`](../../execution/2026-10-30-api-after-fix.md)。

| 文件 | 内容 |
|---|---|
| `report.json` | Playwright JSON 报告（第二轮，66/66 通过，92 KB） |
| `junit.xml` | JUnit XML 报告（第二轮） |

## 复跑

```sh
docker compose down --volumes --remove-orphans
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose build service-oa
docker compose up -d --wait
cd client/guigu-oa-client
DEMO_ORIGIN=http://localhost:9530 npm run test:api
```

trace / HTML 报告不入仓（#122 §4）。本轮无失败，无截图。
