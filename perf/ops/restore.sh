#!/usr/bin/env bash
# 恢复压测快照（#112 §5）：down -v → 恢复卷 → 起 mysql 导入 dump → 起全栈 → 慢 SQL 开关。
# 用法：perf/ops/restore.sh [慢日志文件名]   （默认 slow.log；每次运行换名可保留多轮证据）
set -euo pipefail
cd "$(dirname "$0")/../.."

SNAP=perf/snapshot
SLOW_LOG=${1:-slow.log}

echo ">> down --volumes --remove-orphans"
docker compose down --volumes --remove-orphans

echo ">> 恢复卷"
docker volume create guigu-oa_process-definitions > /dev/null
docker volume create guigu-oa_minio-data > /dev/null
docker run --rm -v guigu-oa_process-definitions:/dst -v "$PWD/$SNAP":/src:ro nginx:alpine \
  sh -c 'tar xzf /src/process-definitions.tgz -C /dst'
docker run --rm -v guigu-oa_minio-data:/dst -v "$PWD/$SNAP":/src:ro nginx:alpine \
  sh -c 'tar xzf /src/minio-data.tgz -C /dst'

echo ">> 起 mysql 并导入快照"
docker compose up -d mysql
until docker compose exec -T mysql mysqladmin ping -h127.0.0.1 -uroot -proot --silent > /dev/null 2>&1; do
  sleep 2
done
gunzip -c "$SNAP/mysql.sql.gz" | docker compose exec -T mysql mysql -uroot -proot

echo ">> 起全栈（--wait 等后端就绪）"
docker compose up -d --wait

echo ">> 打开慢 SQL 日志（$SLOW_LOG, >0.1s）"
docker compose exec -T mysql mysql -uroot -proot -e \
  "SET GLOBAL slow_query_log=ON; SET GLOBAL long_query_time=0.1; SET GLOBAL slow_query_log_file='/var/lib/mysql/$SLOW_LOG';"

echo ">> 等待 /actuator/health UP"
until curl -sf http://localhost:8800/actuator/health | grep -q '"status":"UP"'; do sleep 2; done
echo ">> 恢复完成"
