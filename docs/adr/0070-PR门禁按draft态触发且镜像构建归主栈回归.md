# PR 门禁按 draft 态触发且镜像构建归主栈回归

#276。PR 每次 `synchronize` 都付一次全量 CI（`gh run list` 实测 12–16 分钟，`frontend` job 15m53s 是瓶颈，`backend` 8m29s）。`ci.yml:16-18` 的 `concurrency` 只取消旧 run，不减少最新一次的成本；波次开发用 draft PR 承载中间提交，这些提交本来不需要门禁，却各自跑满一轮。

同时有两处既存浪费：前端单测跑两遍（`npm run verify` 内含 `test:unit`，`ci.yml:79-82` 又跑一遍 `npm run coverage`）；push main 上每个业务镜像被构建两次——`ci.yml:59`/`:106` 一次，Regression 的 `docker compose up -d --build --wait`（`scripts/test-round.sh:287`）又一次，两个 runner 不共享缓存。`ci.yml:57` 的 `mvn -DskipTests package` 产物连它自己的 `docker build` 都没用上：`.dockerignore:1` 排除 `**/target`，镜像内重新 `mvn package`（`lautung-oa-server/Dockerfile:14,26`）。

决定：

- **draft 态不触发**：`ci.yml` 的 `pull_request.types` 显式列出 `opened`、`synchronize`、`reopened`、`ready_for_review`（`ready_for_review` 不在 GitHub 默认类型里）；两个 job 都加 `if: github.event_name == 'push' || github.event.pull_request.draft == false`。draft 期间任何推送零成本，转为 ready 时跑一次，转正后的普通 `synchronize` 照跑。
- **单测只跑一轮且带覆盖率**：`package.json` 拆成 `verify:static`（lint → `check:test-tooling` 九个静态步骤）与末位单测；`verify` = `verify:static && test:unit`，`verify:ci` = `verify:static && test:unit:coverage`。CI 跑 `npm run verify:ci`，覆盖率汇总与断言读这一轮的 `coverage/coverage-summary.json`。`check:test-tooling.mjs` 断言两条链除最后一步外逐字相同，防止以后只往一条链加检查。
- **镜像构建归 Regression**：删掉 `ci.yml` 的两个 `docker build` step。PR 上镜像不是门禁内容；push main 上 `regression.yml` 的 `ci-stack` 会从源码构建两个镜像，是全仓唯一起真实栈的地方。保留 `mvn -DskipTests package` 与 `npm run build:prod` 作为打包/装配验证（`mvn test` 覆盖不到 boot repackage，`vite build` 覆盖不到生产构建）。
- **PR 只装 chromium**：删 `playwright install` 的 `webkit` 参数。PR 门禁只跑 `desktop-chromium` / `mobile-chromium` 两档，WebKit 留给兼容性矩阵。
- **流程约定**：PR 一律 `--draft` 创建；本地 `./scripts/verify.sh` 绿且准备 review 才 `ready_for_review`。写进 `AGENTS.md`，不加 pre-push hook（per-clone、可绕过，还会把每次推送卡几分钟）。

考虑过的替代方案：

- **label 门禁**（否决）：每次开 PR 都要手动打 `ci:run` 之类的标签，忘了就裸奔；本仓没有分支保护，required checks 在免费私有仓不可用（ADR 0030），漏打标签没有任何兜底。
- **`[skip ci]` 提交约定**（否决）：每个中间提交都要记得写，写错了白等 16 分钟；且它同时跳过 push 与 PR 事件，误用会吞掉真正需要的门禁。
- **只在 `ready_for_review` 跑一次**（否决）：转正后 review 期间的修复推送不再有 check，等于把门禁变成一次性快照。
- **按改动路径跳过 backend/frontend job**（否决）：两个 job 并行，墙钟由更慢的 frontend 决定，跳过 backend 只省 Actions 分钟、不减等待；`check:contracts` 两侧都涉及，路径判定漏判就是改了接口却没跑服务端。
- **PR 保留镜像构建、只删 main**（否决）：main 上已被 Regression 重复覆盖；PR 上的镜像构建也不是门禁内容，保留只多付 1–2 分钟。
- **拆 frontend job 并行**（暂缓）：拆成「静态检查+单测」与「e2e+build」可再压墙钟，但要各付一份 `npm ci` 并增加 yml 复杂度；先看 draft 门禁 + 单轮单测后的实测，仍超 10 分钟再动。
- **pre-push hook 跑 `verify.sh`**（否决）：钩子按 clone 配置、可绕过，且会把每次推送阻塞几分钟，制造新的等待。

后果：

- draft 期间 PR 不产生 check；转正前最后一次推送的结果才可见。与 ADR 0030 的文档-only 跳过同类：漂移由本地 `verify.sh` 兜底。
- 改 `Dockerfile` / `compose.yaml` 的 PR 合并后才会在 Regression 暴露；频率低（一年几次），且合并后几分钟内可见。
- push main 的 CI 少两次镜像构建；Regression 的构建是唯一真实镜像构建，失败仍会红在 main 上。
- PR 门禁预期从 12–16 分钟降到 9–10 分钟（落地 PR 的实测值回填于此）。
- `npm run verify` 的本地语义不变（仍跑 `test:unit`，不带覆盖率）；`verify:ci` 只是 CI 组合，本地可手动跑。

Related: #276、ADR 0030（测试常态化拓扑，本条只改 PR 触发与镜像归属）、`README.md`、`docs/agents/testing.md`。
