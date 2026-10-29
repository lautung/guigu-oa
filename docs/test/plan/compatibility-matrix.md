# 兼容性测试：矩阵与判定口径

来源：[确定兼容性测试矩阵与判定口径 #114](https://github.com/lautung/guigu-oa/issues/114) 的 resolution。
执行票：[执行兼容性测试并留证 #117](https://github.com/lautung/guigu-oa/issues/117)。
记录规范：[确定测试数据、缺陷记录与报告规范 #122](https://github.com/lautung/guigu-oa/issues/122)。

本文件定「测什么、怎么判」，不写执行结果；执行记录见 `docs/test/execution/2026-10-29-compatibility.md`。

## 1. 范围与依据

- **最小矩阵**（#108）：Chromium 为主引擎，WebKit 为唯一备选引擎；Firefox 与国产浏览器不启用，
  Edge 与 Chromium 同内核不单列。
- **被测对象**：Compose 栈里客户端 nginx 提供**生产构建**（`docker compose up -d --build --wait`），
  不是 Vite dev server。mock e2e 继续拦截 `/dev-api`，静态资源来自生产构建，保证确定性与真实产物的平衡。
- **全量行为**：`client/guigu-oa-client/tests/e2e/` 的 7 个既有 mock 文件在每个格子里全量执行；
  兼容性自身只补六页扫描，不重写既有用例。
- **真实栈补点**：真实后端 + 真实数据的手机审批链 smoke 在 WebKit 上补一条
  （`tests/e2e-stack/p0-7-mobile-approval.spec.ts` 的 `stack-webkit-mobile` project）。

## 2. 引擎与视口

| 格子 | Playwright project | 视口 | 内容 |
|---|---|---|---|
| 桌面 × Chromium | `desktop-chromium` | 1280×900 | 全量 mock e2e + 六页扫描 |
| 手机 × Chromium | `mobile-chromium` | 390×844，`isMobile` + `hasTouch` | 全量 mock e2e + 六页扫描 |
| 桌面 × WebKit | `desktop-webkit` | 1280×900 | 全量 mock e2e + 六页扫描 |
| 手机 × WebKit | `mobile-webkit` | 390×844，`isMobile` + `hasTouch` | 全量 mock e2e + 六页扫描 |
| 桌面 1440 扫描 | `desktop-1440-chromium` | 1440×900 | 只跑 `compatibility-pages.spec.ts` |

- 平板 768 不设格：`isMobileViewport` 由客户端单测覆盖，「从桌面缩为手机强制离开」由既有 e2e 覆盖。
- 1440 不新增全量 project：内容区 `max-width: 960px`，1440 与 1280 只差两侧留白，只对关键页截图并断言横向溢出。

## 3. 关键页（六页七态）

登录、发起审批（模板列表 + 表单两态）、待处理列表、审批详情、配置-模板编辑、配置-分配角色。
截图命名 `<engine>-<viewport>-<page>-<state>.png`，同时落一份同名 `.console.json`。

配置页在手机视口是**产品规则**而非异常：直接访问落回待处理（ADR 0010/0015），扫描记录为
`config-template-edit-forced-pending` / `config-assign-roles-forced-pending`。

## 4. 判定口径（#114 §5）

任一格出现以下即失败：

1. **页面级横向溢出**：`document.documentElement.scrollWidth > window.innerWidth`（容差 1px）；
2. **控制台错误**：未捕获异常（`pageerror`）或 `console.error`。豁免只接受带证据、写进下列豁免清单的已知噪声；
3. **办事主链路不可完成**：登录 → 发起审批（选模板、填表、抄送、附件、提交）→ 待处理 → 详情 → 通过/拒绝，
   复用 `workflow-smoke.spec.ts`。

产品规则必过项（复用既有 e2e，不新增浏览器用例）：办事在手机视口可完成；配置页在手机或未授予时落到待处理；
已打开配置页后变手机强制离开。

### 豁免清单

| 噪声 | 范围 | 证据与理由 |
|---|---|---|
| `ResizeObserver loop completed with undelivered notifications.` | WebKit，容器尺寸连续变化时以 `error` 事件上报 | 浏览器调度噪声，Chromium 不上报，不影响功能；`tests/e2e/helpers/known-noise.ts` 统一过滤，其他 pageerror 仍失败 |
| `gw.alicdn.com` 图标图片 400 | 模板编辑页的图标下拉 | 外部 CDN，受限网络下缩略图不显示；选项文本仍可选择、保存不受影响。`.console.json` 里计入 `allowedExternalAssetErrors` |

## 5. 已知局限

- 无真机、无云真机；WebKit 是 Safari 的近似，不等于 iOS Safari 实机。
- 不测 Edge（Chromium 同内核）与国产浏览器；不承诺平板宽度。
- 模板图标依赖外部 CDN，离线/内网部署时只显示文字选项。
- 生产构建的 CSP（#139）把浏览器直传限制在 `connect-src` 放行的对象存储源；mock 预签名上传必须使用
  `http://localhost:9000`（`CSP_CONNECT_SRC` 默认值），否则会被 CSP 拦截——这是产品安全行为，不是缺陷。

## 6. 命令

```bash
cd client/guigu-oa-client
npm ci
npx playwright install chromium webkit        # 本机；CI 用 --with-deps

# 栈（仓库根目录）
docker compose up -d --build --wait
./scripts/compose-baseline-check.sh

# 全量矩阵：四格 + 1440，指向生产构建
E2E_BASE_URL=http://127.0.0.1:9530 npm run test:e2e

# 只跑某个格子
E2E_BASE_URL=http://127.0.0.1:9530 npx playwright test --project=desktop-webkit

# 真实栈套件（含 WebKit 手机审批 smoke）
npm run test:e2e:stack
```

不设 `E2E_BASE_URL` 时保持原行为：自动启动 9530 的 Vite dev server（CI 的门禁路径）。
CI 目前仍只跑 `desktop-chromium` + `mobile-chromium`；矩阵是否每次 PR 都跑，留在地图的
「五类测试是否接入 CI 门禁」雾里。

## 7. 证据约定

- Playwright JSON/JUnit：`test-results/compatibility/`（本地，超 #122 单文件上限的 `report.json` 不入仓）；
- 截图与控制台日志：小件入 `docs/test/evidence/<date>-compatibility/`（≤100KB/文件、≤5MB/目录），
  大件/完整报告留在 `test-results/`；
- 失败用例保留 trace（`trace: retain-on-failure`），入口地址写入执行记录；
- 缺陷一 issue，`severity:S1`–`S4` 标签，修复后 `defect:fixed`，closed = verified。
