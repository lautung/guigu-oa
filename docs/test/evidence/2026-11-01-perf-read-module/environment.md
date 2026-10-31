# 环境（perf #182，before/after 两轮同口径）

| 项 | 值 |
|---|---|
| 宿主机 | macOS arm64，10 vCPU / 32 GB |
| Docker Desktop | 29.6.2（VM 10 vCPU / 8 GB，与 #124 基线同口径） |
| Compose 栈 | mysql:8.4（8.4.13）/ redis:7.4-alpine / bitnami MinIO 2025-07-23 / service-oa 本地构建 / client 本地构建 |
| Java | 21（G1，容器内 10 CPU 可见），GC 日志未注入（两轮一致，见执行记录 §偏差） |
| k6 | v2.3.0 `linux/arm64`，`grafana/k6@sha256:9cb0023cc3d7c9b07bf271f554b2942a5b34f82bab6f84130478122493ee590c`（与 #124 同镜像） |
| 造数 | Node 24.18.1（宿主机执行，`localhost:8800/9000`，#119 口径） |
| 数据快照 | 10,001 实例 / 6,000 待办 / 4,001 已办 / 111 附件对象；`mysqldump` 压缩后 2.8MB + 卷 tar；before 轮造数后制作，after 轮复用同一份 |
| before 代码 | main `9f1e27c`（不含 #178 规格任何改动；已含 #147 连接池调优 maximum-pool-size: 25） |
| after 代码 | spec-178 `358dbbd`（#179 显式倒序 + #180 PageWindow + #181 读 module 全部落地） |
| 执行时段 | 2026-11-01 02:06–03:03（before）、03:04–04:03（after，附件/补充档 03:50–04:03 补跑） |
| 两轮差异 | 仅被测代码提交；同机、同快照、同脚本（附件档见执行记录 §偏差：harness 修复）、同镜像 digest |

每轮 `restore.sh` 实测 ~25–37s；after 轮从 worktree 起栈需 `COMPOSE_PROJECT_NAME=guigu-oa`
（`restore.sh` 硬编码 `guigu-oa_*` 卷名，项目名必须是 guigu-oa 才能对上）。
