# 兼容性测试：执行与留证（2026-10-29）

来源：[执行兼容性测试并留证 #117](https://github.com/lautung/guigu-oa/issues/117)。
矩阵与判定口径：[确定兼容性测试矩阵与判定口径 #114](https://github.com/lautung/guigu-oa/issues/114)，
落成文档 `docs/test/plan/compatibility-matrix.md`。
规范：[确定测试数据、缺陷记录与报告规范 #122](https://github.com/lautung/guigu-oa/issues/122)。

**性质：正式执行。** 前置：#114 关闭；#116 安全基线关闭；#128/#129 关闭（一条命令起栈）。

## 执行环境

| 项 | 值 |
|---|---|
| 日期 | 2026-10-29 |
| 被测版本 | 工作区 `28593db`（HEAD，应用代码与 Compose 镜像一致；本票只改测试、CI 与文档） |
| 环境 | 本机 Docker Compose 演示栈（macOS arm64、Docker Desktop、10 vCPU / 8 GB） |
| 被测客户端 | nginx 提供生产构建，`http://127.0.0.1:9530` |
| 栈状态 | `mysql` / `redis` / `minio` / `service-oa` / `client` 全部 Up，`service-oa` healthy |
| 浏览器 | Playwright 1.64.0：Chromium（`chromium-1248`）、WebKit（`webkit-2370`） |
| 起栈方式 | 沿用既有栈，未重建；执行前 `docker compose ps` 与基线检查均通过 |

执行前环境验收：

```
./scripts/compose-baseline-check.sh   → 结果：26 通过，0 失败
node scripts/compose-demo-smoke.mjs   → Compose demo OK: Bob pending=304, ended=49, Carol copied=353, attachments=1
```

（待处理数量较大是前序功能与安全套件累积的演示数据，不影响 mock 矩阵；真实栈 smoke 只按本轮 runId 断言。）

## 矩阵执行

命令（仓库根目录已起栈；四格 + 1440 一次跑完）：

```bash
cd client/guigu-oa-client
npx playwright install webkit
E2E_BASE_URL=http://127.0.0.1:9530 \
  npx playwright test --reporter=list,json,junit
```

结果：**145 通过 / 0 失败 / 44 跳过，189 次运行，50.8 s**。

| 格子 | 通过 | 跳过 | 失败 | 说明 |
|---|---:|---:|---:|---|
| 桌面 × Chromium（1280×900） | 45 | 1 | 0 | 跳过项是只在手机 project 运行的配置页用例 |
| 桌面 × WebKit（1280×900） | 45 | 1 | 0 | 同上 |
| 手机 × Chromium（390×844） | 25 | 21 | 0 | 21 项是配置页/配置写用例的既有手机跳过 |
| 手机 × WebKit（390×844） | 25 | 21 | 0 | 同上；办事主链路均通过 |
| 1440 × Chromium（关键页扫描） | 5 | 0 | 0 | 登录、发起两态、待处理+详情、模板编辑、分配角色 |

跳过项都是既定视口设计（#114 §2）：配置页在手机视口不可占用；桌面 project 跳过「手机尺寸」用例。
没有「执行失败」或「环境不可用」导致的跳过。

### 六页扫描（每格）

`compatibility-pages.spec.ts` 在五个 project 中执行，逐页截图并判定横向溢出、`pageerror`、`console.error`：

| 页 | 桌面两引擎 | 手机两引擎 | 1440 Chromium |
|---|---|---|---|
| 登录 | 截图通过 | 截图通过 | 截图通过 |
| 发起审批-模板列表 | 截图通过 | 截图通过 | 截图通过 |
| 发起审批-表单 | 截图通过 | 截图通过 | 截图通过 |
| 待处理列表 | 截图通过 | 截图通过 | 截图通过 |
| 审批详情 | 截图通过 | 截图通过 | 截图通过 |
| 配置-模板编辑 | 可占用 | 落回待处理（产品规则） | 可占用 |
| 配置-分配角色 | 弹窗可打开 | 落回待处理（产品规则） | 弹窗可打开 |

所有格子 `scrollWidth ≤ innerWidth + 1`；除豁免清单中的外部图标 CDN 外，控制台无错误。

## 产品规则结论

1. **办事必须能在手机浏览器完成：通过。**
   `workflow-smoke.spec.ts` 在手机 × Chromium 与手机 × WebKit 都完成
   登录 → 发起（含附件）→ 待处理 → 详情 → 通过/拒绝；真实栈 `P0-7` 在 WebKit 手机视口
   完成 登录 → 待处理 → 详情 → 通过，且无横向溢出。
2. **配置页在手机或未授予时落到待处理：通过。**
   `mobile-chromium` / `mobile-webkit` 两张扫描截图均为 `config-*-forced-pending`；
   既有 `template-edit` / `assign-roles` 的「手机尺寸不能占用」用例在两个引擎的桌面 project 中通过。
3. **已打开配置页后变手机强制离开：通过。**
   `assign-roles`「变成手机尺寸时强制离开配置并丢弃未保存勾选」与
   `template-edit`「普通离开可取消，缩到手机时优先强制离开并关闭丢弃确认」
   在桌面 × Chromium 与桌面 × WebKit 均通过（手机 project 中按既定规则跳过）。

## 真实栈补充 smoke

```bash
cd client/guigu-oa-client
npm run test:e2e:stack
```

结果：**27 通过 / 0 失败，1.2 min**。其中 Chromium 26 条沿用功能基线，
新增 `stack-webkit-mobile` 一条：WebKit 390×844 真实登录 → 待处理 → 详情 → 通过。

## 失败项与缺陷

- 矩阵与真实栈 smoke **0 失败**，本轮未新增缺陷。
- 执行中修正了两处「测试与生产构建不一致」，均不是产品缺陷：
  1. **CSP 拦截 mock 直传**：生产构建的 `connect-src` 只放行 `http://localhost:9000`（#139），
     mock 预签名上传原先指向 `https://objects.test` / `https://object-storage.test`，WebKit 严格按 CSP
     拒绝而 Chromium 的请求拦截未触发同一路径。已把所有 e2e mock 的上传/下载源改为
     `http://localhost:9000`，与真实部署一致。
  2. **WebKit 的 ResizeObserver 噪声**：容器尺寸连续变化时 WebKit 以 `error` 事件上报
     `ResizeObserver loop completed with undelivered notifications.`，Chromium 不上报。
     已用 `tests/e2e/helpers/known-noise.ts` 只过滤这一条，其他 `pageerror` 仍失败。

## 已知局限（写入报告口径）

- 无真机、无云真机；WebKit 是 Safari 的近似，不等于 iOS Safari 实机。
- 不测 Edge（Chromium 同内核）与国产浏览器；不承诺平板宽度。
- 模板图标来自 `gw.alicdn.com` 外部 CDN，受限网络下返回 400，只影响图标缩略图；
  选择、保存与页面交互不受影响，已记入豁免清单（`allowedExternalAssetErrors`）。
- 生产构建的 CSP 要求附件直传源与 `CSP_CONNECT_SRC` 成对配置；mock 与评测均按默认
  `http://localhost:9000` 对齐。

## 证据

- `docs/test/evidence/2026-10-29-compatibility/`：五格 35 张关键页截图、同名控制台 JSON、JUnit 报告
  `report.xml`（小件入仓，符合 #122：≤100KB/文件、≤5MB/目录）。
- 完整 Playwright JSON 报告（252KB，超过单文件上限）：本机
  `client/guigu-oa-client/test-results/compatibility/report.json`，复跑可重新生成。
- 真实栈报告：`client/guigu-oa-client/tests/e2e-stack/test-results/e2e-stack/report.json` 与 `report.xml`（本机）。
- 本轮无失败用例，因此没有 trace；`trace: retain-on-failure` 保持开启。

## 复跑方式

1. 起栈（或沿用运行中的栈）：`docker compose up -d --build --wait`，
   `./scripts/compose-baseline-check.sh` 应为 26 通过、0 失败；
2. `cd client/guigu-oa-client && npx playwright install chromium webkit`；
3. 矩阵：`E2E_BASE_URL=http://127.0.0.1:9530 npm run test:e2e`；
4. 真实栈：`npm run test:e2e:stack`；
5. 不设 `E2E_BASE_URL` 时自动起 Vite dev server，用于本地/CI 的 Chromium 门禁路径。
