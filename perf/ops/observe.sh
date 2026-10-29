#!/usr/bin/env bash
# docker stats 采样器：~1Hz 采样全部容器到 CSV，持续 $2 秒。
# 用法：perf/ops/observe.sh <label> <seconds>
set -euo pipefail
cd "$(dirname "$0")/../.."

LABEL=${1:?label}
SECONDS_ARG=${2:?seconds}
OUT="perf/results/stats-$LABEL.csv"

echo "time,container,cpu_pct,mem_used,mem_limit,net_io,block_io" > "$OUT"
END=$((SECONDS + SECONDS_ARG))
while [ "$SECONDS" -lt "$END" ]; do
  docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}}' \
    | sed "s/^/$(date +%H:%M:%S),/" >> "$OUT"
  sleep 1
done
echo ">> 采样完成：$OUT"
