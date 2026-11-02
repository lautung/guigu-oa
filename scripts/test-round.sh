#!/usr/bin/env bash
# 五类测试一键复跑（#119）：把 docs/test/plan.md §8 的入口按「接口 → 功能 → 性能 →
# 安全 → 兼容性」串起来，每步打印耗时并写入 $OUT/timings.tsv。
#
#   ./scripts/test-round.sh all          # 全流程（含性能全档位，约 50–60 min）
#   ./scripts/test-round.sh clean        # 干净起栈 + 26 项基线检查 + 冒烟 + 基线快照
#   ./scripts/test-round.sh api          # 接口套件两轮（要求干净数据态）
#   ./scripts/test-round.sh functional   # 真实栈功能套件
#   ./scripts/test-round.sh perf         # 造数 10k + 快照 + 全档位（约 40 min）
#   ./scripts/test-round.sh security     # 恢复基线快照 + 61 条定向 + ZAP + 存储型 XSS
#   ./scripts/test-round.sh compat       # 兼容性矩阵 + 真实栈 smoke
#   ./scripts/test-round.sh ci-stack     # 干净起栈 + 接口 + 功能 + 兼容矩阵（push main 用）
#   ./scripts/test-round.sh ci-security  # 干净起栈 + 61 条定向 + ZAP + 存储型 XSS（周任务用）
#
# 环境变量（括号内为默认值）：
#   DEMO_ORIGIN      (http://localhost:9530)  浏览器/接口套件入口
#   E2E_ORIGIN       (http://127.0.0.1:9530)  兼容性矩阵页面入口（nginx 生产构建）
#   MAVEN_MIRROR_URL / NPM_REGISTRY (空)      受限网络构建镜像源，透传 compose build
#                                             （api/functional/compat/security 会先重建镜像）
#   BASE_SNAPSHOT    (/tmp/guigu-oa-base-snapshot)  clean 产出的干净基线快照
#   OUT              (docs/test/evidence/<日期>-clean-rerun) 证据目录
#   ZAP_HOME         (/tmp/zap/ZAP_2.17.0)    ZAP Core 解压目录（下载配方见 docs/agents/testing.md「安全套件」）
#   PERF_TARGET / PERF_FINISHED (10000 / 4000)
#   FORCE=1          允许覆盖已存在的证据文件（ZAP/security-scan 的重跑保护）
#
# 前置：Docker Desktop（8 GB 可用内存）、Node 22+、JDK 21；端口 3306/6379/9000/9001/8800/9530
# 空闲。安全/性能只打本机 Compose，不做 DoS 与主动扫描（#113）。
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

DEMO_ORIGIN=${DEMO_ORIGIN:-http://localhost:9530}
E2E_ORIGIN=${E2E_ORIGIN:-http://127.0.0.1:9530}
BASE_SNAPSHOT=${BASE_SNAPSHOT:-/tmp/guigu-oa-base-snapshot}
OUT=${OUT:-docs/test/evidence/$(date +%Y-%m-%d)-clean-rerun}
ZAP_HOME=${ZAP_HOME:-/tmp/zap/ZAP_2.17.0}
PERF_TARGET=${PERF_TARGET:-10000}
PERF_FINISHED=${PERF_FINISHED:-4000}
FORCE=${FORCE:-0}
client=client/guigu-oa-client
timings="$OUT/timings.tsv"

usage() {
  cat <<'EOF'
五类测试一键复跑（#119）：把 docs/test/plan.md §8 的入口按「接口 → 功能 → 性能 →
安全 → 兼容性」串起来，每步打印耗时并写入 $OUT/timings.tsv。

  ./scripts/test-round.sh all          # 全流程（含性能全档位，约 50–60 min）
  ./scripts/test-round.sh clean        # 干净起栈 + 26 项基线检查 + 冒烟 + 基线快照
  ./scripts/test-round.sh api          # 接口套件两轮（要求干净数据态）
  ./scripts/test-round.sh functional   # 真实栈功能套件
  ./scripts/test-round.sh perf         # 造数 10k + 快照 + 全档位（约 40 min）
  ./scripts/test-round.sh security     # 恢复基线快照 + 61 条定向 + ZAP + 存储型 XSS
  ./scripts/test-round.sh compat       # 兼容性矩阵 + 真实栈 smoke
  ./scripts/test-round.sh ci-stack     # 干净起栈 + 接口 + 功能 + 兼容矩阵（push main 用）
  ./scripts/test-round.sh ci-security  # 干净起栈 + 61 条定向 + ZAP + 存储型 XSS（周任务用）

环境变量（括号内为默认值）：
  DEMO_ORIGIN      (http://localhost:9530)  浏览器/接口套件入口
  E2E_ORIGIN       (http://127.0.0.1:9530)  兼容性矩阵页面入口（nginx 生产构建）
  MAVEN_MIRROR_URL / NPM_REGISTRY (空)      受限网络构建镜像源，透传 compose build
  BASE_SNAPSHOT    (/tmp/guigu-oa-base-snapshot)  clean 产出的干净基线快照
  OUT              (docs/test/evidence/<日期>-clean-rerun) 证据目录
  ZAP_HOME         (/tmp/zap/ZAP_2.17.0)    ZAP Core 解压目录（下载配方见 docs/agents/testing.md「安全套件」）
  PERF_TARGET / PERF_FINISHED (10000 / 4000)
  FORCE=1          允许覆盖已存在的证据文件（ZAP/security-scan 的重跑保护）

前置：Docker Desktop（8 GB 可用内存）、Node 22+、JDK 21；端口 3306/6379/9000/9001/8800/9530
空闲。安全/性能只打本机 Compose，不做 DoS 与主动扫描（#113）。
EOF
}

say() { printf '>> [%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# step <名称> <命令...>：计时并记录到 $OUT/timings.tsv。
step() {
  local label=$1
  shift
  local started ended
  started=$(date +%s)
  say "开始：$label"
  "$@"
  ended=$(date +%s)
  local seconds=$((ended - started))
  printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$label" "$seconds" >> "$timings"
  say "完成：${label}（${seconds}s）"
}

need() {
  command -v "$1" > /dev/null 2>&1 || die "缺少命令：$1"
}

preflight() {
  mkdir -p "$OUT"
  [ -f "$timings" ] || printf 'time\tstep\tseconds\n' > "$timings"
  need docker
  need node
  need curl
  need jq
  docker compose version > /dev/null 2>&1 || die "Docker Compose v2 不可用"
  [ -d "$client/node_modules" ] || die "缺少 $client/node_modules：先 cd $client && npm ci"
}

require_k6() {
  docker image inspect grafana/k6:latest > /dev/null 2>&1 || die \
    "缺少 grafana/k6:latest。受限网络获取：crane pull --platform linux/arm64 grafana/k6:latest k6.tar && docker load -i k6.tar"
}

require_zap() {
  [ -d "$ZAP_HOME" ] || die "缺少 ZAP Core：${ZAP_HOME}（可用 ZAP_HOME 覆盖）"
}

# 兼容性矩阵与真实栈 smoke 都依赖 nginx 生产构建；dev server 是本地开发路径，不在复跑口径内。
require_stack_origin() {
  curl -sf "$DEMO_ORIGIN/dev-api/actuator/health" > /dev/null \
    || curl -sf "$DEMO_ORIGIN" > /dev/null \
    || die "$DEMO_ORIGIN 不可用，先起 Compose 栈（./scripts/test-round.sh clean）"
}

# 套件打的是正在跑的镜像。源码改过而没重建时，`up -d` 会沿用旧 jar：
# 新用例红得莫名其妙，旧套件则对着旧代码假绿。带 --build 起一次，有层缓存时只是几秒。
refresh_stack() {
  step "stack: up --build --wait lautung-oa-server client" \
    docker compose up -d --build --wait lautung-oa-server client
}

snapshot_base() {
  say "制作干净基线快照 → $BASE_SNAPSHOT"
  perf/ops/snapshot.sh
  rm -rf "$BASE_SNAPSHOT"
  mkdir -p "$BASE_SNAPSHOT"
  cp -a perf/snapshot/. "$BASE_SNAPSHOT/"
}

restore_base() {
  [ -f "$BASE_SNAPSHOT/mysql.sql.gz" ] || die \
    "缺少基线快照 ${BASE_SNAPSHOT}（先跑 ./scripts/test-round.sh clean）"
  say "恢复干净基线快照 → perf/snapshot"
  rm -rf perf/snapshot
  mkdir -p perf/snapshot
  cp -a "$BASE_SNAPSHOT/." perf/snapshot/
}

phase_clean() {
  preflight
  step "clean: down --volumes --remove-orphans" docker compose down --volumes --remove-orphans
  step "clean: up --build --wait" docker compose up -d --build --wait
  step "clean: baseline-check" ./scripts/compose-baseline-check.sh
  step "clean: demo-smoke" node scripts/compose-demo-smoke.mjs
  step "clean: snapshot" snapshot_base
}

phase_api() {
  preflight
  refresh_stack
  require_stack_origin
  step "api: round 1" env DEMO_ORIGIN="$DEMO_ORIGIN" npm --prefix "$client" run test:api
  step "api: round 2" env DEMO_ORIGIN="$DEMO_ORIGIN" npm --prefix "$client" run test:api
  mkdir -p "$OUT/api"
  cp "$client/test-results/api/junit.xml" "$OUT/api/junit.xml"
  # raw report.json 按大件规范不入仓（#122），蒸馏成 stats+用例清单：76 例已 103KB 超限。
  jq '{stats: .stats, tests: [.. | objects | select(has("ok")) | {title: .title, ok: .ok}]}' \
    "$client/test-results/api/report.json" > "$OUT/api/summary.json"
}

phase_functional() {
  preflight
  refresh_stack
  require_stack_origin
  step "functional: e2e-stack" env DEMO_ORIGIN="$DEMO_ORIGIN" npm --prefix "$client" run test:e2e:stack
  # e2e-stack 的 config 在 tests/e2e-stack 下，JSON/JUnit 相对 config 目录落盘。
  mkdir -p "$OUT/functional"
  cp "$client/tests/e2e-stack/test-results/e2e-stack/report.xml" "$OUT/functional/junit.xml"
  jq '{stats: .stats, tests: [.. | objects | select(has("ok")) | {title: .title, ok: .ok}]}' \
    "$client/tests/e2e-stack/test-results/e2e-stack/report.json" > "$OUT/functional/summary.json"
}

phase_perf() {
  preflight
  require_k6
  [ -f "$BASE_SNAPSHOT/mysql.sql.gz" ] || die \
    "缺少基线快照 ${BASE_SNAPSHOT}（先跑 ./scripts/test-round.sh clean）"
  # 造数在宿主机执行：seed.mjs 直连 localhost:8800/9000，预签名 URL 的 Host 与签名一致。
  # 不要改成容器内执行并改写 MINIO_PUT_BASE —— Host 参与 SigV4 签名，PUT 会 403。
  step "perf: restore base" perf/ops/restore.sh slow-seed.log
  step "perf: seed ${PERF_TARGET}/${PERF_FINISHED}" env TARGET="$PERF_TARGET" FINISHED="$PERF_FINISHED" node perf/seed.mjs
  step "perf: snapshot" perf/ops/snapshot.sh
  perf/ops/row-counts.sh > perf/results/rows-seed.txt 2> /dev/null || true
  step "perf: smoke" perf/ops/round.sh smoke smoke
  step "perf: baseline-1" perf/ops/round.sh baseline-1 baseline
  step "perf: baseline-2" perf/ops/round.sh baseline-2 baseline
  step "perf: baseline-3" perf/ops/round.sh baseline-3 baseline
  step "perf: mid-1" perf/ops/round.sh mid-1 mid
  step "perf: stress-1" perf/ops/round.sh stress-1 stress
  step "perf: attachment-1" perf/ops/round.sh attachment-1 baseline perf/attachment.js
  mkdir -p "$OUT/perf"
  cp perf/results/seed-summary.json "$OUT/perf/seed-summary.json"
  cp perf/results/rows-seed.txt "$OUT/perf/rows-seed.txt" 2> /dev/null || true
  for name in smoke baseline-1 baseline-2 baseline-3 mid-1 stress-1 attachment-1; do
    cp "perf/results/$name.txt" "$OUT/perf/k6-console-$name.txt"
    jq '{name, scenarios, metrics: (.metrics | with_entries(.value |= {values, thresholds}))}' \
      "perf/results/$name.json" > "$OUT/perf/k6-summary-$name.json"
    cp "perf/results/rows-$name.txt" "$OUT/perf/rows-$name.txt" 2> /dev/null || true
    # 慢日志复制成 .txt：仓库 .gitignore 忽略 *.log，证据需要能直接入仓。
    cp "perf/results/slow-$name.log" "$OUT/perf/slow-$name.txt" 2> /dev/null || true
    if [ -f "perf/results/stats-$name.csv.gz" ]; then
      cp "perf/results/stats-$name.csv.gz" "$OUT/perf/stats-$name.csv.gz"
    elif [ -f "perf/results/stats-$name.csv" ]; then
      gzip -c "perf/results/stats-$name.csv" > "$OUT/perf/stats-$name.csv.gz"
    fi
  done
}

security_suite() {
  mkdir -p "$OUT/security"
  local force=()
  [ "$FORCE" = 1 ] && force=(--force)
  step "security: directed scan" node scripts/security-scan.mjs --out "$OUT/security" ${force[@]+"${force[@]}"}
  step "security: zap passive" env ZAP_HOME="$ZAP_HOME" node scripts/zap-baseline.mjs --out "$OUT/security" ${force[@]+"${force[@]}"}
  # 完整 ZAP HTML/JSON 不入仓（证据规范 #122），留在本机。
  local zap_full="/tmp/security-evidence-full/$(basename "$OUT")"
  if [ -d "$OUT/security/zap-full" ]; then
    mkdir -p "$zap_full"
    mv "$OUT/security/zap-full" "$zap_full/zap-full"
    say "完整 ZAP 报告：$zap_full/zap-full/zap-report.html"
  fi
  step "security: stored xss" env DEMO_ORIGIN="$DEMO_ORIGIN" npm --prefix "$client" run test:security
  cp "$client/tests/security/test-results/xss-report.json" "$OUT/security/xss-report.json"
  cp "$client/tests/security/test-results/xss-report.xml" "$OUT/security/junit-xss.xml"
}

phase_security() {
  preflight
  refresh_stack
  require_zap
  step "security: restore base" perf/ops/restore.sh slow-security.log
  security_suite
}

phase_ci_security() {
  preflight
  require_zap
  step "ci-security: down --volumes" docker compose down --volumes --remove-orphans
  step "ci-security: up --build --wait" docker compose up -d --build --wait
  step "ci-security: baseline-check" ./scripts/compose-baseline-check.sh
  step "ci-security: demo-smoke" node scripts/compose-demo-smoke.mjs
  security_suite
}

compat_matrix() {
  # mock 矩阵的 reporter 由配置决定；这里显式加 JSON/JUnit，证据可归档。
  local reporters='list,json,junit'
  local started ended seconds
  started=$(date +%s)
  say "开始：compat: mock matrix"
  (
    cd "$client"
    PLAYWRIGHT_JSON_OUTPUT_NAME=test-results/compatibility/report.json \
      PLAYWRIGHT_JUNIT_OUTPUT_NAME=test-results/compatibility/report.xml \
      E2E_BASE_URL="$E2E_ORIGIN" npx playwright test --reporter="$reporters"
  )
  ended=$(date +%s)
  seconds=$((ended - started))
  printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "compat: mock matrix" "$seconds" >> "$timings"
  say "完成：compat: mock matrix（${seconds}s）"

  # 先归档截图与控制台日志：后续套件会清理默认 outputDir（client/test-results）。
  mkdir -p "$OUT/compat"
  cp -a "$client/test-results/compatibility/." "$OUT/compat/"
}

phase_compat() {
  preflight
  refresh_stack
  require_stack_origin
  compat_matrix

  step "compat: e2e-stack" env DEMO_ORIGIN="$DEMO_ORIGIN" npm --prefix "$client" run test:e2e:stack
  cp "$client/tests/e2e-stack/test-results/e2e-stack/report.xml" "$OUT/compat/e2e-stack-junit.xml"
}

phase_ci_stack() {
  preflight
  step "ci-stack: down --volumes" docker compose down --volumes --remove-orphans
  step "ci-stack: up --build --wait" docker compose up -d --build --wait
  step "ci-stack: baseline-check" ./scripts/compose-baseline-check.sh
  step "ci-stack: demo-smoke" node scripts/compose-demo-smoke.mjs
  phase_api
  phase_functional
  require_stack_origin
  compat_matrix
}

phase_all() {
  phase_clean
  phase_api
  phase_functional
  phase_perf
  phase_security
  phase_compat
  say "全流程完成；耗时汇总：$timings"
}

case "${1:-}" in
  clean) phase_clean ;;
  api) phase_api ;;
  functional) phase_functional ;;
  perf) phase_perf ;;
  security) phase_security ;;
  compat) phase_compat ;;
  ci-stack) phase_ci_stack ;;
  ci-security) phase_ci_security ;;
  all) phase_all ;;
  -h | --help | help) usage; exit 0 ;;
  *) usage; exit 2 ;;
esac

# verify 常在生成证据之前跑过；这里在每轮收尾立刻查体积/链接，避免超大证据到下一轮才红。
if [ -d "$OUT" ]; then
  step "evidence: check-docs" node "$client/scripts/check-docs.mjs"
fi

say "耗时汇总：$timings"
