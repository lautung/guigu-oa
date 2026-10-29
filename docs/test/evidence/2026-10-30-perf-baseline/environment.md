# 环境（perf #124）

| 项 | 值 |
|---|---|
| 宿主机 | macOS arm64，10 vCPU / 32 GB |
| Docker Desktop | 29.6.2（VM 10 vCPU / 8 GB，与 `docs/development/compose-demo.md` 同基线） |
| Compose 栈 | mysql:8.4（8.4.13）/ redis:7.4-alpine / bitnami MinIO 2025-07-23 / service-oa 本地构建 / client 本地构建 |
| Java | 21.0.12（G1，容器内 10 CPU 可见 / 7.9 GB 堆默认） |
| k6 | v2.3.0 `linux/arm64`，`grafana/k6@sha256:9cb0023cc3d7c9b07bf271f554b2942a5b34f82bab6f84130478122493ee590c` |
| crane | 0.22.1（镜像获取路径见 `perf/README.md`） |
| 造数 | Node 24.21（`node:24-slim` 容器内执行，绕过宿主机 TUN fake-IP） |
| 数据快照 | 10,001 实例（10,000 造数 + 1 演示）/ 6,000 待办 / 4,001 已办 / 111 个附件对象；`mysqldump` 压缩后 2.9MB + 卷 tar |
| 压测期间 | 停 `client` 容器与系统 MySQL/Redis；service-oa 注入 `JAVA_TOOL_OPTIONS`（GC 日志 + OOM dump）与 `S3_PUBLIC_ENDPOINT=host.docker.internal:9000`（预签名 host 需容器可达） |
| 提交 | 见仓库 `perf/` 与本目录的入库版本（k6 脚本版本 = 执行版本） |

每轮 `restore.sh` 实测 ~37s（down -v → 卷还原 → 导 dump → `up --wait` → 慢 SQL 开关）。
