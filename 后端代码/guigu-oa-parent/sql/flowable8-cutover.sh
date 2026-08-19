#!/usr/bin/env bash
# Flowable 8 切库演练：确认无审批中实例后，可选备份并 DROP 全部 ACT_% / FLW_% 引擎表。
# 不改 oa_* / sys_*，不改审批模板状态。
# 用法：
#   ./flowable8-cutover.sh              # 闸门 + DROP
#   ./flowable8-cutover.sh --backup-dir /tmp/act-backup
#   ./flowable8-cutover.sh --dry-run
# 环境变量：MYSQL_HOST MYSQL_PORT MYSQL_USER MYSQL_PASSWORD MYSQL_DATABASE
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DRY_RUN=0
BACKUP_DIR=""

usage() {
  sed -n '2,10p' "$0"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --backup-dir) BACKUP_DIR="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 1 ;;
  esac
done

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-guigu-oa}"

if ! command -v mysql >/dev/null 2>&1; then
  echo "切库停止：未找到 mysql 客户端" >&2
  exit 1
fi

mysql_cli() {
  mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" \
    --database="$MYSQL_DATABASE" --batch --raw --skip-column-names "$@"
}

IN_PROGRESS="$(mysql_cli -e "SELECT COUNT(*) FROM oa_process WHERE status=1 AND is_deleted=0;")"
if [[ "${IN_PROGRESS}" -gt 0 ]]; then
  echo "切库停止：oa_process 仍有 ${IN_PROGRESS} 条审批中（status=1）" >&2
  exit 1
fi
echo "硬前提通过：oa_process.status=1 为 0 条"

ENGINE_TABLES="$(mysql_cli -e "
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND (TABLE_NAME LIKE 'ACT\\_%' ESCAPE '\\\\' OR TABLE_NAME LIKE 'FLW\\_%' ESCAPE '\\\\')
ORDER BY TABLE_NAME;
")"

if [[ -z "${ENGINE_TABLES}" ]]; then
  echo "没有 ACT_% / FLW_% 引擎表可 DROP（空库或已切过）。可以继续以 Flowable 8 启动建表。"
  exit 0
fi

echo "将 DROP 的引擎表："
echo "${ENGINE_TABLES}"

if [[ "${DRY_RUN}" -eq 1 ]]; then
  echo "dry-run：未执行 DROP"
  exit 0
fi

if [[ -n "${BACKUP_DIR}" ]]; then
  if ! command -v mysqldump >/dev/null 2>&1; then
    echo "切库停止：指定了 --backup-dir 但未找到 mysqldump" >&2
    exit 1
  fi
  mkdir -p "${BACKUP_DIR}"
  BACKUP_FILE="${BACKUP_DIR}/act-flw-$(date +%Y%m%d%H%M%S).sql"
  echo "备份引擎表到 ${BACKUP_FILE}（此备份不得再被 Flowable 连接）"
  # shellcheck disable=SC2086
  mysqldump --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" \
    --skip-comments --skip-lock-tables "$MYSQL_DATABASE" ${ENGINE_TABLES} > "${BACKUP_FILE}"
fi

DROP_SQL="SET FOREIGN_KEY_CHECKS=0;"
while IFS= read -r table; do
  [[ -z "${table}" ]] && continue
  DROP_SQL="${DROP_SQL} DROP TABLE IF EXISTS \`${table}\`;"
done <<< "${ENGINE_TABLES}"
DROP_SQL="${DROP_SQL} SET FOREIGN_KEY_CHECKS=1;"

mysql_cli -e "${DROP_SQL}"
echo "已 DROP 全部 ACT_% / FLW_% 引擎表。oa_* / sys_* 未改动。"
echo "下一步见 ${SCRIPT_DIR}/flowable8-cutover.md"
