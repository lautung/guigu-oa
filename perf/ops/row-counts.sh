#!/usr/bin/env bash
# 压测前后的表行数（#112 §6）。用法：perf/ops/row-counts.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

docker compose exec -T mysql mysql -uroot -proot -N -e "
SELECT 'oa_process', COUNT(*) FROM \`guigu-oa\`.oa_process
UNION ALL SELECT 'oa_process_record', COUNT(*) FROM \`guigu-oa\`.oa_process_record
UNION ALL SELECT 'ACT_RU_TASK', COUNT(*) FROM \`guigu-oa\`.ACT_RU_TASK
UNION ALL SELECT 'ACT_RU_IDENTITYLINK', COUNT(*) FROM \`guigu-oa\`.ACT_RU_IDENTITYLINK
UNION ALL SELECT 'ACT_HI_TASKINST', COUNT(*) FROM \`guigu-oa\`.ACT_HI_TASKINST
UNION ALL SELECT 'ACT_HI_PROCINST', COUNT(*) FROM \`guigu-oa\`.ACT_HI_PROCINST
UNION ALL SELECT 'ACT_HI_ACTINST', COUNT(*) FROM \`guigu-oa\`.ACT_HI_ACTINST
UNION ALL SELECT 'ACT_GE_BYTEARRAY', COUNT(*) FROM \`guigu-oa\`.ACT_GE_BYTEARRAY;"
