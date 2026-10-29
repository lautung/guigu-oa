# 接口测试证据（2026-10-29 基线轮）

来源：[执行接口测试并留证 #123](https://github.com/lautung/guigu-oa/issues/123)。
执行记录：`docs/test/execution/2026-10-29-api.md`。

| 文件 | 内容 |
|---|---|
| `report.json` | Playwright JSON 报告，65 用例全绿，耗时 14.3 s |
| `junit.xml` | JUnit XML（同一轮，供 CI/报告引用） |

被测版本 `f01b250`，全新卷 Compose 演示栈，入口 `http://localhost:9530/dev-api`。
trace 与 HTML 报告不入仓（#122 §4）；本轮无失败，无截图。

## 复跑

```bash
cd client/guigu-oa-client
DEMO_ORIGIN=http://localhost:9530 npm run test:api
```

报告输出到 `client/guigu-oa-client/test-results/api/`（`report.json` + `junit.xml`）。

## after-fix

#127 修复后复跑到 `docs/test/evidence/2026-10-29-api-after-fix/`，重点看 4 个缺陷用例：
`b-user.spec.ts` B5（#143）、`b-role.spec.ts` B8（#144）、`b-grant.spec.ts` 第二例（#146）、
`f-http-semantics.spec.ts` 整组（#145）。这些用例当前断言的是缺陷行为，修复后应翻红并按新契约改写。
