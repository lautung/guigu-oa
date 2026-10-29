#!/usr/bin/env bash
# 一轮压测结束后的取证：慢 SQL、GC 日志、表行数。必须在下一次 restore 之前执行
# （restore 的 down -v 会删卷，慢日志与 GC 日志随容器消失）。
# 用法：perf/ops/collect.sh <label>
set -euo pipefail
cd "$(dirname "$0")/../.."

LABEL=${1:?label}
OUT=perf/results
mkdir -p "$OUT"

# 慢 SQL（restore.sh 按轮命名的日志仍在 mysql 容器里）
docker compose exec -T mysql sh -c "cat /var/lib/mysql/slow-${LABEL}.log 2>/dev/null" \
  > "$OUT/slow-${LABEL}.log" || echo "（无 ${LABEL} 慢日志）" > "$OUT/slow-${LABEL}.log"

# GC 日志（多轮共用一个容器，按时间窗口在分析期切分；此处抓当前全部）
docker compose exec -T service-oa sh -c "gzip -c /tmp/gc.log /tmp/gc.log.* 2>/dev/null" \
  > "$OUT/gc-${LABEL}.log.gz" || echo "（无 GC 日志）" > "$OUT/gc-${LABEL}.log"

# 行数
perf/ops/row-counts.sh 2>/dev/null > "$OUT/rows-after-${LABEL}.txt"

echo ">> collect ${LABEL} 完成"
