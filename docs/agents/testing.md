# Testing

测试怎么分层、挂载工具怎么用、服务端单元测试有哪些坑。命令见下节「命令」；覆盖率口径见 README「测试与覆盖率」。

五类测试（功能、接口、性能、安全、兼容性）的资产在 `docs/test/`：`plan.md` 定测什么与怎么判，`execution/` 一文件一轮，`report.md` 是结论与缺陷账本，`evidence/README.md` 是证据索引。性能资产见 `perf/README.md`（压测命令以它为准；造数必须在宿主机 `localhost` 执行，容器改写预签名 Host 会 403）。一键复跑用 `./scripts/test-round.sh all`，按类子命令见 `--help`。常态化拓扑见 ADR 0030：PR 维持轻量，push main 跑栈回归，每周跑安全，性能只在本地。

手工打栈的登录与调用范式见 `scripts/compose-demo-smoke.mjs`。可发起的模板只由 `/admin/process/findStartable` 回答：`oa_process_template.status = 1` 只是「已发布」，发布产物不可核验时仍不可发起。

## 命令

前端（都在 `client/guigu-oa-client/` 下执行）：

- `npm run verify`：lint + typecheck + format:check + 文档检查（模块地图、Markdown 相对链接、证据路径与体积）+ 旧品牌词检查 + shell 检查 + 合同断言 + 测试工具检查 + 单测（提交前必跑）。
- `npm run test:e2e`：Playwright；默认自动启动 9530 dev server，设置 `E2E_BASE_URL` 指向已构建客户端时不再启动。
- `npm run test:api`：接口套件（打已运行的 Compose 栈，见 `playwright.api.config.ts`）。**需要干净演示栈**：`globalSetup` 检测到压测造数会直接报错，先 `docker compose down --volumes --remove-orphans && docker compose up -d --build --wait`。改了服务端必须带 `--build`：直接 `up -d` 会沿用旧 jar，新用例红得莫名其妙、旧套件假绿。
- `npm run build:prod`：类型检查 + 生产构建。

仓库：

- `./scripts/verify.sh`：客户端 `npm run verify` + 服务端 `mvn -pl lautung-oa-server -am test`。
- `./scripts/test-round.sh all`：五类测试一键复跑（按类子命令见 `--help`；方案与证据在 `docs/test/`）。常态化拓扑见 ADR 0030：PR 轻量，push main 跑栈回归，每周跑安全，性能只在本地。

服务端（`server/lautung-oa-parent/`）：

- `mvn -pl lautung-oa-server -am test`：合同测试用 H2 + 内存 Redis，不需要本地 MySQL/Redis；纯单测随域模块，`@SpringBootTest` 集中在 `lautung-oa-server`，`-am` 会带上全部上游模块的测试（全量等价于父目录 `mvn test`）。
- 改过 `src/test/resources` 后先 `mvn clean test`：Maven 不会清理 `target/test-classes` 里已删除的资源，旧配置会继续生效。
- 单次运行会刷出数十 KB 结构化日志：重定向到文件后 `grep "Tests run:"` 看汇总；不要加 `-q`（会吞掉 surefire 汇总）。

## 分层

- module 测试是主：phase、迟到归属、写后策略等规则打在 interface 上。
- view 挂载测试只覆盖模板状态分支与命令式接线，状态由 seam mock 驱动；不靠 view 测试重新断言 module 内部规则。理由见 `docs/agents/client-modules.md` 的约定。

## 客户端

- 共享工具 `tests/unit/support/harness.js`：`setupSession()`（pinia + 假 recognition）、`fakeRecognition()`、`installHttp(responder)`、`mountOptions(pinia, router)`、`elementStubs`。
- 全局 setup `tests/unit/setup.js` 由 `test.setupFiles` 加载：已注册 auto-unmount 与 nprogress stub，**不要**在单个 spec 里再写 `enableAutoUnmount`。
- 挂载 view 的常规流程：`setupSession()` → `createTestRouter([...])` → `router.push(...)` + `isReady()` → `mount(View, mountOptions(pinia, router))`。
- HTTP 用 axios adapter：`installHttp((config) => ok(data) / paged(records))`，每个 spec 的 `afterEach` 里 `http.restore()`。错误响应也归它：responder 抛 AxiosError 原样冒泡、抛其他错误包装成 `ERR_NETWORK`——#145/#155 的错误形状一处拥有（`renewal.spec` 是现成用例）。
- mock 调用次数断言用 `toHaveBeenCalledTimes`：`toHaveLength` 查的是函数形参个数（`vi.fn((config) => …)` 的 `length` 恒为 1），在 mock 上会静默错断；数组长度仍用 `toHaveLength`。
- 需要真实 Element Plus 渲染 `el-table`/`el-dialog` 插槽时：`vi.mock('element-plus', importOriginal)` 只替换 `ElMessage`/`ElMessageBox`，挂载时加 `plugins: [pinia, router, ElementPlus]` 与 `ensureDomApis()`。纯 stub 渲染不到 scoped slot，模板分支不会执行。
- Element Plus 必须留在 `vite.config.js` 的 `test.server.deps.inline`：外置时其内部 import 的 async-validator 与测试侧形成两份实例，`el-form.validate()` 静默失真（空必填判 VALID、错误不上屏），生产 rollup 单实例无此问题。删掉 inline 会让依赖校验的视图测试变红，所以配置由套件自守；排障前先看 ADR 0038，别重查一遍。
- EP 错误上屏走 100ms `refDebounced`：断言 `.el-form-item__error` 前真等 ~150ms 再 `flushPromises()`，`flushPromises` 本身不等真时间。
- 状态机 view（编辑器、分配角色、详情、发起页）优先 mock 模块接缝（`vi.mock('@/config/template-editor', ...)`），用 `reactive` state + spy 驱动各 phase，不启动真实模块。
- mock 工厂里不要引用顶层 `const`（TDZ）：先 `vi.hoisted(() => ({ holder: {} }))`，再在 `beforeEach` 里给 `holder.x` 赋值。

## e2e

- 默认 `npm run test:e2e` 起 9530 的 Vite dev server；设置 `E2E_BASE_URL` 时指向已构建客户端并跳过 dev server（兼容性矩阵，见 `docs/test/plan/compatibility-matrix.md`）。
- 验证未提交的本地改动时，Compose 客户端占着 9530，dev server 的 `strictPort` 起不来：先 `docker compose stop client`，跑完 `docker compose start client` 恢复；跑栈上的已构建产物才走 `E2E_BASE_URL`。
- 种会话 cookie 一律用 `tests/e2e/helpers/base-url.ts` 的 `e2eCookieURL()`（它是 `E2E_BASE_URL` 的唯一解读者）。硬编码 `domain` 会让 `localhost` 与 `127.0.0.1` 两种入口表现不一致，失败点还会落在与该用例无关的断言上。
- e2e helper 等**确认态正信号**——持久化完成后才出现的 UI 状态（如上传行内出现「删除」按钮与文件大小），不等上传开始时乐观渲染的名字；等错信号时守卫会静默拒绝，失败点落在后续无关断言上（#192）。
- project：`desktop-chromium` / `mobile-chromium` / `desktop-webkit` / `mobile-webkit` 全量；`desktop-1440-chromium` 只跑 `compatibility-pages.spec.ts`。CI 门禁仍只跑 Chromium 两档。
- **报告路径按 Playwright config 所在目录解析**：`tests/e2e-stack/` 与 `tests/security/` 的 JSON/JUnit 落在各自 `test-results/` 下，接口与 mock 套件在客户端根 `test-results/`。每次运行会清空自己的 outputDir，手工连着跑多套时先归档再跑下一套（`scripts/test-round.sh` 已内置归档顺序）。
- **间歇用差异定性，不悬置**：套件内红、隔离单跑绿 ⇒ 顺序/并发依赖，直接进取证；套件内同用例两连红即停按「间歇」处理。「留观」必须带触发条件（再复现即根因）——#169 轮留观的 p0-3 附件竞态拖到 #192 轮才根因，多烧两轮套件。
- **失败取证**：先看用例目录下的 `error-context.md`（页面快照 + 错误）；更深证据解 `test-results/<用例>/trace.zip`——`*-trace.network` 查请求是否发出与响应体、`*.trace` 查 console/pageError、`screencast/` 按文件名 epoch-ms 挑点击前后帧看 toast/校验错误。隔离复跑会覆盖 retain-on-failure 留下的 trace，取证前先拷走。
- 生产构建有 CSP（#139）：mock 预签名上传使用 `http://localhost:9000`（`connect-src` 默认放行源），不要再用 `objects.test` 之类的外部主机。
- WebKit 会把 ResizeObserver 循环上报成 `pageerror`；用 `tests/e2e/helpers/known-noise.ts` 只过滤这一条，其他错误仍失败。

## 服务端

- 编译先行：改装配签名或贴着既有代码边界重构后，先 `mvn -pl lautung-oa-server -am compile`（秒级）再进全量 test——漏回构造参数、Edit 边界重复声明这类错不必等全量回环暴露。
- 服务/控制器用纯 Mockito，不启动 Spring：`mock(Mapper.class)` + `ReflectionTestUtils.setField(service, "baseMapper", mapper)` 注入字段。
- MyBatis-Plus 的 `LambdaQueryWrapper` 在纯单元测试里需要先初始化元数据，否则抛 `can not find lambda cache`：
  `TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Entity.class)`。
- `BaseMapper` 有重载，mock 时要类型化 matcher：`selectList(isNull())`、`insert(any(Entity.class))`、`update(isNull(), any())`；裸 `any()` 会编译歧义或匹配不到。
- MyBatis `Interceptor` 的签名覆盖以走真实 `Plugin` 装配的合同测试为准：单测直调 `intercept()` 绕过 `@Signature` 匹配，签名漏注册时照样全绿（#151 曾因此漏掉 `update` 签名，靠评审才抓住）。
- 新增或改断言先证明它会红：临时去掉被保护的行为（删掉 SQL 的 `<include>`、把文案改回旧值、拆掉签名注册），确认新断言变红并指名到语句/路径，再恢复。仍绿说明断言没落在被保护的那条路径上——绿的摆设比没有更贵（#198 的护栏与契约测试都是这么验的，护栏另加了「解析到 0 条语句即红」的防呆）。
- 文件上传用 `@TempDir` + `MockMultipartFile`；S3 相关 mock `S3Client` / `S3Presigner` 返回 `Presigned*Request` 的 mock，`url()` 直接 stub。
- 真实 Spring 合同测试沿用现有 `@SpringBootTest` + H2 + 内存 Redis，不需要本地 MySQL/Redis；它们全在装配模块 `lautung-oa-server`。单测随域模块走：多数是纯 Mockito；少数需要装配资源的两例（`OpenApiExposureConfigTest` 读 profile YAML、`ProcessEngineCutoverTest` 起真 Flowable + BPMN 夹具）仍留在域模块，资源由 pom 的 `testResources` 从 `lautung-oa-server` 复制。
- 要起真实服务做验证（非合同测试）时：连 Compose 栈已发布的 MySQL/Redis（3306/6379），`java -jar target/lautung-oa-server.jar --server.port=8801 --spring.flyway.validate-on-migrate=false`——Compose 镜像带 V100/101 而本地 main 已无，关校验后两边迁移互补、零写入。不动 8800 的栈，测完 DROP 自己建的临时库。
- 每份 H2 schema 只裁到该组用例需要的表，**不是全库**：`auth-contract-schema.sql` 只有 `sys_user` / `sys_menu`（没有角色与授予行，配置 gate 恒拒绝），所以逐字段校验用例放那里成立（校验先于 gate），正向路径要用 `FlywayMigrationContractTest`（真迁移 + 种子授予）。新增合同测试前先看它挂的 schema 有没有你要的表。脚本由 `@Sql` 在每个测试方法前重放，状态靠末尾的 `DELETE FROM` 重置，负例（插一条软删行之类）可以直接写。
- 加索引后必须用实发 SQL 验证执行计划：「索引建了 ≠ 优化器会用」——MySQL 8.4 在 join 上下文按 ref 成本取舍（差 0.45 即反转，`ANALYZE TABLE` 与显式 DESC 索引都救不回），优化器不认排序收益时 mapper 条件 hint 是合法兜底（#185）。实发 SQL 抓取与 EXPLAIN 配方见 `perf/README.md`「SQL 取证纪律」。
- 单类/多类：`mvn -pl lautung-oa-server -am test -Dtest=A,B -Dsurefire.failIfNoSpecifiedTests=false`；漏掉最后一个参数会因上游模块没有匹配用例而整轮失败。
- 收紧入参规则（新增 `@Valid`/约束）时，grep `tests/api` 与 `tests/e2e-stack` 的负例载荷：仍返回 201 不代表走到了命名分支，必要时用 `expectCodeAndMessage` 钉住稳定消息片段。
- 服务端合同测试同样受这条约束，且由 `npm run check:contracts`（`scripts/check-contract-assertions.mjs`）强制：断言 201 的用例必须同链钉 `$.message`；同一检查还禁止控制器把 MyBatis 实体当 `@RequestBody`、禁止 `*SaveRequest` 声明 `id`。205 不要求钉消息——它的文案由两个 producer 统一为「没有操作权限」，码加上 `status().isOk()` 已能指认分支。

## 安全套件

- ZAP Core 2.17.0 默认放 `/tmp/zap/ZAP_2.17.0`，`test-round.sh ci-security` 可用 `ZAP_HOME` 覆盖。首次准备（GitHub 资产名是 `ZAP_2.17.0_Core.zip`，不是 Linux `tar.gz`）：

  ```sh
  mkdir -p /tmp/zap
  curl -L -o /tmp/zap/ZAP_2.17.0_Core.zip \
    https://github.com/zaproxy/zaproxy/releases/download/v2.17.0/ZAP_2.17.0_Core.zip
  unzip -q /tmp/zap/ZAP_2.17.0_Core.zip -d /tmp/zap
  ```

- 完整 ZAP HTML/JSON 留在 `/tmp/security-evidence-full/<轮次>/`，入仓只留摘要；`check-docs` 拦单文件 >100KB、单轮 >5MB，`test-round.sh` 每轮收尾会自动跑它。

## 主栈回归

- `.github/workflows/regression.yml` 用 `concurrency: regression-${{ github.ref }}` + `cancel-in-progress: true`：后续 main push 会取消正在跑的前一个 Regression，状态是 `cancelled`，不是失败。
- 合并 PR 后若还要继续直推 main：先等 merge commit 的 CI + Regression 绿，或事后对 merge SHA `gh run rerun <run-id>`；若接受后续 run 覆盖，在票/PR 里写清被取消的 run 与覆盖它的 run。

## 覆盖率

- `./scripts/coverage.sh`：两端一起跑并打印汇总；CI 用它同一份 `scripts/coverage-summary.mjs` 输出 summary。
- 覆盖率只报告、不卡红；`npm run verify` 与 `scripts/verify.sh` 不包含覆盖率步骤。
- 服务端 JaCoCo 聚合报告在 `lautung-oa-coverage-report/target/site/jacoco-aggregate/`；分母口径与排除项见 README「测试与覆盖率」。
- `@vitest/coverage-v8` 必须与 `vitest` 同版本，`npm run check:test-tooling` 会校验。
