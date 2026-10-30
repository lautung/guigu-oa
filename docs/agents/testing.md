# Testing

测试怎么分层、挂载工具怎么用、服务端单元测试有哪些坑。命令与覆盖率口径见 README「测试与覆盖率」。

五类测试（功能、接口、性能、安全、兼容性）的资产在 `docs/test/`：`plan.md` 定测什么与怎么判，`execution/` 一文件一轮，`report.md` 是结论与缺陷账本，`evidence/README.md` 是证据索引。性能资产见 `perf/README.md`（压测命令以它为准；造数必须在宿主机 `localhost` 执行，容器改写预签名 Host 会 403）。一键复跑用 `./scripts/test-round.sh all`，按类子命令见 `--help`。常态化拓扑见 ADR 0030：PR 维持轻量，push main 跑栈回归，每周跑安全，性能只在本地。

## 分层

- module 测试是主：phase、迟到归属、写后策略等规则打在 interface 上。
- view 挂载测试只覆盖模板状态分支与命令式接线，状态由 seam mock 驱动；不靠 view 测试重新断言 module 内部规则。理由见 `docs/agents/client-modules.md` 的约定。

## 客户端

- 共享工具 `tests/unit/support/harness.js`：`setupSession()`（pinia + 假 recognition）、`fakeRecognition()`、`installHttp(responder)`、`mountOptions(pinia, router)`、`elementStubs`。
- 全局 setup `tests/unit/setup.js` 由 `test.setupFiles` 加载：已注册 auto-unmount 与 nprogress stub，**不要**在单个 spec 里再写 `enableAutoUnmount`。
- 挂载 view 的常规流程：`setupSession()` → `createTestRouter([...])` → `router.push(...)` + `isReady()` → `mount(View, mountOptions(pinia, router))`。
- HTTP 用 axios adapter：`installHttp((config) => ok(data) / paged(records))`，每个 spec 的 `afterEach` 里 `http.restore()`。
- 需要真实 Element Plus 渲染 `el-table`/`el-dialog` 插槽时：`vi.mock('element-plus', importOriginal)` 只替换 `ElMessage`/`ElMessageBox`，挂载时加 `plugins: [pinia, router, ElementPlus]` 与 `ensureDomApis()`。纯 stub 渲染不到 scoped slot，模板分支不会执行。
- 状态机 view（编辑器、分配角色、详情、发起页）优先 mock 模块接缝（`vi.mock('@/config/template-editor', ...)`），用 `reactive` state + spy 驱动各 phase，不启动真实模块。
- mock 工厂里不要引用顶层 `const`（TDZ）：先 `vi.hoisted(() => ({ holder: {} }))`，再在 `beforeEach` 里给 `holder.x` 赋值。

## e2e

- 默认 `npm run test:e2e` 起 9530 的 Vite dev server；设置 `E2E_BASE_URL` 时指向已构建客户端并跳过 dev server（兼容性矩阵，见 `docs/test/plan/compatibility-matrix.md`）。
- project：`desktop-chromium` / `mobile-chromium` / `desktop-webkit` / `mobile-webkit` 全量；`desktop-1440-chromium` 只跑 `compatibility-pages.spec.ts`。CI 门禁仍只跑 Chromium 两档。
- **报告路径按 Playwright config 所在目录解析**：`tests/e2e-stack/` 与 `tests/security/` 的 JSON/JUnit 落在各自 `test-results/` 下，接口与 mock 套件在客户端根 `test-results/`。每次运行会清空自己的 outputDir，手工连着跑多套时先归档再跑下一套（`scripts/test-round.sh` 已内置归档顺序）。
- 生产构建有 CSP（#139）：mock 预签名上传使用 `http://localhost:9000`（`connect-src` 默认放行源），不要再用 `objects.test` 之类的外部主机。
- WebKit 会把 ResizeObserver 循环上报成 `pageerror`；用 `tests/e2e/helpers/known-noise.ts` 只过滤这一条，其他错误仍失败。

## 服务端

- 服务/控制器用纯 Mockito，不启动 Spring：`mock(Mapper.class)` + `ReflectionTestUtils.setField(service, "baseMapper", mapper)` 注入字段。
- MyBatis-Plus 的 `LambdaQueryWrapper` 在纯单元测试里需要先初始化元数据，否则抛 `can not find lambda cache`：
  `TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Entity.class)`。
- `BaseMapper` 有重载，mock 时要类型化 matcher：`selectList(isNull())`、`insert(any(Entity.class))`、`update(isNull(), any())`；裸 `any()` 会编译歧义或匹配不到。
- 文件上传用 `@TempDir` + `MockMultipartFile`；S3 相关 mock `S3Client` / `S3Presigner` 返回 `Presigned*Request` 的 mock，`url()` 直接 stub。
- 真实 Spring 合同测试沿用现有 `@SpringBootTest` + H2 + 内存 Redis，不需要本地 MySQL/Redis。

## 覆盖率

- `./scripts/coverage.sh`：两端一起跑并打印汇总；CI 用它同一份 `scripts/coverage-summary.mjs` 输出 summary。
- 覆盖率只报告、不卡红；`npm run verify` 与 `scripts/verify.sh` 不包含覆盖率步骤。
- `@vitest/coverage-v8` 必须与 `vitest` 同版本，`npm run check:test-tooling` 会校验。
