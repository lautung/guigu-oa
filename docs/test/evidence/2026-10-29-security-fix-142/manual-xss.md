# 审批表单值存储型 XSS 人工复核（#142）

- 被测版本：`aabb4ad`（`fix: render approval form values as text instead of HTML (#142)`）
- 栈：本机 Compose 演示栈（`SPRING_PROFILES_ACTIVE=dev,demo`，5 容器；`client` 与 `service-oa` 本轮重建）
- 入口：`http://localhost:9530`（API 经 `/dev-api`），匿名面 `http://localhost:8800`
- 定向结果：本目录 `security-results.json`（SEC-XSS-01 通过）

## 命令

```bash
docker compose up -d --build --wait client
node scripts/security-scan.mjs --only SEC-XSS --out docs/test/evidence/2026-10-29-security-fix-142
```

SEC-XSS-01 用 Playwright 在真实栈上执行：alice 通过 `startUp` 把
`<img src=x onerror="window.__secXss=1">` 写进「请假原因」，bob 登录后在待处理列表找到该审批、
读取 `window.__secXss`，再点进详情重复读取；本轮还断言两处可见文本包含原样载荷。

```
> playwright test --config tests/security/playwright.security.config.ts --grep XSS
Running 1 test using 1 worker
  ✓  1 [security-chromium] › tests/security/security-xss.spec.ts:85:1 › 审批表单字段的存储型 XSS 不执行 (4.0s)
  1 passed (4.6s)
```

## 修复前后

| 观察点 | 基线轮（`docs/test/evidence/2026-10-29-security-baseline`） | 本轮（`aabb4ad`） |
|---|---|---|
| 列表页 `WorkListPage` 的 `summaryFields` | `v-html` 渲染，`onerror` 执行，`xss-executed.png` 留证 | `{{ field.value }}` 文本插值，`window.__secXss` 为空，列表项文本包含 `<img src=x onerror="window.__secXss=1">` |
| 详情页 `formFields` | `v-html` 渲染，同一载荷再次执行 | 文本插值，`window.__secXss` 为空，`.form-field` 文本包含原样载荷 |
| 客户端源码 | `src` 内 2 处 `v-html` | `src` 内 0 处；`vue/no-v-html` 提为 error |
| 构建产物 | 页面脚本含 `v-html` 编译结果 | 容器内 `grep -rl v-html /usr/share/nginx/html` 无匹配 |

基线证据里的 `security-results.json`（SEC-XSS-01 失败）与 `xss-report.json`、`xss-executed.png`
保持原样，本轮只新增通过结果，不覆盖基线。

## 守卫

| 层 | 守卫 | 结果 |
|---|---|---|
| Lint（`npm run verify` / CI） | `vue/no-v-html: error`；探针 `src/__vhtml_probe.vue` 报 `error 'v-html' directive can lead to XSS attack` | 通过 |
| 客户端模块单测 | `form-values.spec.js`：字符串/数字/布尔/空值/数组/对象的文本规整与键序 | 3/3 通过 |
| 客户端视图单测 | 列表页与详情页：载荷值不生成 `img` 元素、页面文本保留原样字符 | 2/2 通过 |
| 客户端全量 | `npm run verify` | lint / typecheck / format / docs / 493 单测通过 |
| 服务端 | `mvn -pl service-oa -am test`（`./scripts/verify.sh`） | 323 通过 |
| 真实栈 | `SEC-XSS-01`（Playwright） | 1/1 通过 |
| 真实栈功能套件 | `npm run test:e2e:stack` | 26/26 通过（1.1m） |

## 红绿验证

修复前的视图用例确实会失败（补丁只改了模板与呈现层，先加用例后改实现）：

- 列表页：`expect(wrapper.find('img').exists()).toBe(false)` 收到 `true`——`v-html` 把载荷变成了
  `<img src="x">` 元素；
- 详情页：同一断言同样收到 `true`；
- 把 `v-html` 写回任一 `.vue` 时 `npm run lint` 以 `vue/no-v-html` 失败；
- `form-values` 单测在模块不存在时导入失败，实现后通过。

说明这些断言钉住的是渲染方式，而不是“恰好没有执行”。

## 工具修正

首次复跑在 fixture 阶段失败：`发起后在已发起列表找不到 sec-…-a`。原因是演示库累积到 280 条
已发起审批，`findStarted` 按创建顺序升序返回，新审批落在末页，而脚本只翻前 5 页（250 条）。
`scripts/security-scan.mjs` 改为先读第 1 页拿 `total`，再从末页往回找；审批本身已创建成功
（末页最后一条即该 marker），不是发起失败。修正后同一命令通过，未重置演示数据。
