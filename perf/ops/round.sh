#!/usr/bin/env bash
# 一档压测的完整执行器（#112 §5 协议）：restore → 停 client → observe → k6 →
# **当档拷出慢日志与行数** → 启 client。每档一条命令，结果文件都以 <name> 区分。
#
# 用法：perf/ops/round.sh <name> [tier] [script]
#   name   轮次名（如 baseline-1、attachment-1）
#   tier   k6 TIER：smoke / baseline / mid / stress（默认 baseline；附件档忽略）
#   script 默认 perf/mixed.js；附件档传 perf/attachment.js（脚本自动注入 S3_PUBLIC_ENDPOINT 覆写）
#   OBS_SECONDS 可覆盖 docker stats 采样秒数（默认按档位推断）
#
# 为什么必须在当档收尾取证：每档 restore.sh 都会 down -v 重建数据卷，
# 慢日志随卷销毁；整轮结束后再拷只能看到最后一档（2026-10-30 #147 复跑重犯 #124 的教训）。
set -euo pipefail
cd "$(dirname "$0")/../.."

NAME=${1:?用法: perf/ops/round.sh <name> [tier] [script]}
TIER=${2:-baseline}
SCRIPT=${3:-perf/mixed.js}
R=perf/results
mkdir -p "$R"

# 参数顺序是 <name> 在前：误把档位名当 name 传会静默跑错档并且文件名全错，这里立刻拒绝。
case "$TIER" in
  smoke|baseline|mid|stress) ;;
  *)
    echo "未知 TIER: ${TIER}（可选 smoke/baseline/mid/stress；第 1 个参数是轮次名）" >&2
    exit 2
    ;;
esac

ATTACHMENT=0
case "$SCRIPT" in *attachment.js) ATTACHMENT=1 ;; esac
if [ -n "${OBS_SECONDS:-}" ]; then
  OBS=${OBS_SECONDS}
elif [ "$ATTACHMENT" = 1 ]; then
  OBS=240
else
  case "$TIER" in
    smoke) OBS=60 ;;
    mid) OBS=300 ;;
    stress) OBS=540 ;;
    *) OBS=420 ;;
  esac
fi

echo ">> [$(date +%H:%M:%S)] restore（慢日志 slow-$NAME.log）"
perf/ops/restore.sh "slow-$NAME.log" | tee "$R/restore-$NAME.log"

if [ "$ATTACHMENT" = 1 ]; then
  # 预签名 URL 的 host 参与签名，不能用改写 host 的方式换地址：把公网端点指到容器可达地址。
  trap 'rm -f compose.override.yaml' EXIT
  cat > compose.override.yaml <<'YAML'
services:
  service-oa:
    environment:
      S3_PUBLIC_ENDPOINT: http://host.docker.internal:9000
YAML
  docker compose up -d --wait service-oa
fi

echo ">> [$(date +%H:%M:%S)] k6 TIER=$TIER <- $SCRIPT"
docker compose stop client > /dev/null
perf/ops/observe.sh "$NAME" "$OBS" > "$R/observe-$NAME.log" 2>&1 &
OBS_PID=$!
docker run --rm -i -w /results -v "$PWD/$R:/results" grafana/k6:latest run \
  -e PERF_BASE=http://host.docker.internal:8800 -e TIER="$TIER" \
  -e SUMMARY_NAME="$NAME" - < "$SCRIPT" 2>&1 | tee "$R/$NAME.txt"
wait "$OBS_PID" 2>/dev/null || true
docker compose start client > /dev/null

if [ "$ATTACHMENT" = 1 ]; then
  rm -f compose.override.yaml
  docker compose up -d --wait service-oa
fi

echo ">> [$(date +%H:%M:%S)] 当档取证：行数 / 慢日志 / stats"
perf/ops/row-counts.sh > "$R/rows-$NAME.txt" 2>&1 || true
docker compose exec -T mysql sh -c "cat /var/lib/mysql/slow-$NAME.log 2>/dev/null" \
  > "$R/slow-$NAME.log" 2>/dev/null || true
gzip -f "$R/stats-$NAME.csv" 2>/dev/null || true
echo ">> [$(date +%H:%M:%S)] 完成：$R/$NAME.txt / slow-$NAME.log / rows-$NAME.txt"
