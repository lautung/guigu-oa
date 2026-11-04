## Agent skills

Per-repo configuration for [Matt Pocock skills 1.3](https://github.com/mattpocock/skills/releases/tag/v1.3.1).

### 侦察

侦察先派 Explore agent：一次一个事实域（DDL、客户端载荷、service 行为分开问），报告只要结论 + `file:line`；要改的文件自己读。

### Pull requests

创建或更新 PR（包括草稿 PR）前，必须读取并使用 Matt Pocock 的 `pr` skill（在当前可用 skills 中定位 `pr/SKILL.md`），按其模板编写 PR 正文。

### Commits

一个 ADR / issue 完成即提交，不把两轮改动留在同一工作区：收尾时 `git status` 只应剩一个主题的改动。混在一起后只能按 hunk 手工拆分，拆分本身会静默丢内容。

### Issue tracker

Issues live in this repo's GitHub Issues (via the `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical roles map 1:1 to tracker labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

从 `needs-triage` 直接实现一个 issue 时，先用 `file:line` 对着代码核验它的事实主张，再定方案——主张可能就是错的（#156 写「空列表返回 200」，实际早已是 201 失败）。

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. Before exploring the codebase or naming domain concepts, read `docs/agents/domain.md`.

### Module map

`docs/agents/client-modules.md` indexes the client modules and server approval ports with their interfaces; read it before grepping for a seam. `docs/agents/server-modules.md` maps the server modules (where `Result`/异常处理/安全过滤链/权限失效 live) and the request-rejection order of the security chain.

## 项目环境与运行

目标产品是**统一客户端** `client/guigu-oa-client`（Vue 3.5 + Vite 8 + Element Plus + Pinia）配合后端 `server/lautung-oa-parent`（多模块 Maven；Spring Boot 4.1.0、JDK 21、Flowable 8.0.0 Process）。旧的 `管理端`/`员工端`/`其他` 前端不是目标产品。

- 本地启动（服务与端口、后端 jar、MySQL/Redis、DB 初始化、种子账号、Compose 卷名坑）：`docs/development/local-dev.md`
- 命令与测试（前端 verify/e2e/api/build、仓库 verify、服务端测试、五类测试轮、覆盖率、安全套件、主栈回归）：`docs/agents/testing.md`
- Compose 演示栈：`docs/development/compose-demo.md`
- 提交前门禁：`./scripts/verify.sh`。
