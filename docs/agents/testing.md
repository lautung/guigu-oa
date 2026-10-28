# Testing

测试怎么分层、挂载工具怎么用、服务端单元测试有哪些坑。命令与覆盖率口径见 README「测试与覆盖率」。

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
