#!/usr/bin/env bash
# 制作压测快照（#112 §5）：mysqldump（含 ACT_*/FLW_*）+ process-definitions 卷 + minio-data 卷。
# 用法：perf/ops/snapshot.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

OUT=perf/snapshot
mkdir -p "$OUT"

echo ">> mysqldump guigu-oa"
docker compose exec -T mysql mysqldump -uroot -proot \
  --single-transaction --quick --routines --triggers --hex-blob \
  --databases guigu-oa | gzip > "$OUT/mysql.sql.gz"

echo ">> 打包 process-definitions 卷"
docker run --rm -v guigu-oa_process-definitions:/src:ro -v "$PWD/$OUT":/out nginx:alpine \
  tar czf /out/process-definitions.tgz -C /src .

echo ">> 打包 minio-data 卷"
docker run --rm -v guigu-oa_minio-data:/src:ro -v "$PWD/$OUT":/out nginx:alpine \
  tar czf /out/minio-data.tgz -C /src .

ls -lh "$OUT"
echo ">> 快照完成"
