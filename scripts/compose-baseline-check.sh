#!/usr/bin/env bash
# 校验 Compose 演示栈是否达到可复现测试基线：容器、健康、安全响应头、匿名暴露面、
# 种子账号、引擎表、演示审批。
# 用法：在仓库根目录执行 ./scripts/compose-baseline-check.sh
set -uo pipefail

DB_CONTAINER="${DB_CONTAINER:-guigu-oa-mysql-1}"
API="${API:-http://localhost:8800}"
CLIENT="${CLIENT:-http://localhost:9530}"
PASSWORD="${DEMO_PASSWORD:-111111}"

pass=0
fail=0

ok() { printf '  \033[32mPASS\033[0m %s\n' "$1"; pass=$((pass + 1)); }
bad() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; fail=$((fail + 1)); }

sql() {
  docker exec "$DB_CONTAINER" mysql -uroot -proot -N -B -e "$1" guigu-oa 2>/dev/null
}

echo "== 容器 =="
for svc in mysql redis minio service-oa client; do
  state=$(docker compose ps --format '{{.Service}} {{.State}}' 2>/dev/null | awk -v s="$svc" '$1==s {print $2}')
  [ "$state" = "running" ] && ok "$svc running" || bad "$svc 未运行（${state}）"
done

echo "== 健康 =="
code=$(curl -sS -m 10 -o /tmp/baseline-health.json -w '%{http_code}' "$API/actuator/health" 2>/dev/null)
if [ "$code" = "200" ] && grep -q '"status":"UP"' /tmp/baseline-health.json; then
  ok "后端 /actuator/health UP"
else
  bad "后端 /actuator/health 异常（HTTP ${code}）"
fi
code=$(curl -sS -m 10 -o /tmp/baseline-login.html -w '%{http_code}' "$CLIENT/login" 2>/dev/null)
if [ "$code" = "200" ] && grep -q '<div id="app">' /tmp/baseline-login.html; then
  ok "客户端登录页可访问"
else
  bad "客户端登录页异常（HTTP ${code}）"
fi

echo "== 安全响应头 =="
headers=$(curl -sSI -m 10 "$CLIENT/login" 2>/dev/null)
if printf '%s' "$headers" | grep -qi "^content-security-policy:.*frame-ancestors 'none'"; then
  ok "客户端 CSP 含 frame-ancestors 'none'"
else
  bad "客户端缺 CSP 或 frame-ancestors"
fi
if printf '%s' "$headers" | grep -qi '^x-frame-options: *DENY'; then
  ok "客户端 X-Frame-Options: DENY"
else
  bad "客户端缺 X-Frame-Options: DENY"
fi
if printf '%s' "$headers" | grep -qi '^x-content-type-options: *nosniff'; then
  ok "客户端 X-Content-Type-Options: nosniff"
else
  bad "客户端缺 X-Content-Type-Options: nosniff"
fi

echo "== 匿名暴露面 =="
code=$(curl -sS -m 10 -o /tmp/baseline-apidocs.json -w '%{http_code}' "$API/v3/api-docs" 2>/dev/null)
if [ -z "$code" ] || [ "$code" = "000" ]; then
  bad "后端不可达，无法确认 /v3/api-docs（HTTP ${code:-无}）"
elif [ "$code" = "200" ] && grep -q '"openapi"' /tmp/baseline-apidocs.json; then
  bad "后端 /v3/api-docs 匿名可读"
else
  ok "后端 /v3/api-docs 未匿名暴露（HTTP ${code}）"
fi
code=$(curl -sS -m 10 -o /tmp/baseline-swagger.html -w '%{http_code}' "$API/swagger-ui/index.html" 2>/dev/null)
if [ -z "$code" ] || [ "$code" = "000" ]; then
  bad "后端不可达，无法确认 /swagger-ui/index.html（HTTP ${code:-无}）"
elif [ "$code" = "200" ] && grep -qi 'swagger' /tmp/baseline-swagger.html; then
  bad "后端 /swagger-ui/index.html 匿名可读"
elif [ "${code#5}" != "$code" ]; then
  bad "后端 /swagger-ui/index.html 返回 5xx（HTTP ${code}）"
else
  ok "后端 /swagger-ui/index.html 未匿名暴露（HTTP ${code}）"
fi
headers=$(curl -sSI -m 10 "$CLIENT/" 2>/dev/null)
server_header=$(printf '%s' "$headers" | grep -i '^server:' | head -1)
if ! printf '%s' "$headers" | grep -q '^HTTP/'; then
  bad "客户端不可达，无法确认 Server 头"
elif printf '%s' "$server_header" | grep -Eq '[0-9]+\.[0-9]+'; then
  bad "客户端 Server 头暴露版本（${server_header}）"
else
  ok "客户端 Server 头不含版本（${server_header:-未返回}）"
fi

echo "== 种子账号 =="
for user in admin user alice bob carol; do
  body=$(curl -sS -m 15 -X POST "$API/admin/system/index/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"password\":\"$PASSWORD\"}" 2>/dev/null)
  if printf '%s' "$body" | grep -q '"token"'; then
    ok "$user 登录成功"
  else
    bad "$user 登录失败：$(printf '%s' "$body" | head -c 120)"
  fi
done

echo "== 数据库与引擎表 =="
act=$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='guigu-oa' AND table_name LIKE 'ACT\\_%'")
flw=$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='guigu-oa' AND table_name LIKE 'FLW\\_%'")
biz=$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='guigu-oa' AND (table_name LIKE 'sys\\_%' OR table_name LIKE 'oa\\_%')")
[ "${act:-0}" -gt 0 ] && ok "Flowable ACT_* 表 ${act} 张" || bad "Flowable ACT_* 表缺失"
[ "${flw:-0}" -gt 0 ] && ok "Flowable FLW_* 表 ${flw} 张" || bad "Flowable FLW_* 表缺失"
[ "${biz:-0}" -gt 0 ] && ok "业务 sys_*/oa_* 表 ${biz} 张" || bad "业务表缺失"

users=$(sql "SELECT GROUP_CONCAT(username ORDER BY id) FROM sys_user WHERE is_deleted=0")
if [ "$users" = "admin,user,alice,bob,carol" ]; then
  ok "种子用户齐全：$users"
else
  bad "种子用户不符：$users"
fi

echo "== 演示审批 =="
pub=$(sql "SELECT COUNT(*) FROM oa_process_template_publication")
pending=$(sql "SELECT COUNT(*) FROM ACT_RU_TASK")
ended=$(sql "SELECT COUNT(*) FROM ACT_HI_PROCINST")
cc=$(sql "SELECT COUNT(*) FROM oa_process_cc WHERE is_deleted=0")
[ "${pub:-0}" -gt 0 ] && ok "模板发布记录 ${pub} 条" || bad "没有模板发布记录，演示审批无法发起"
[ "${pending:-0}" -gt 0 ] && ok "待处理任务 ${pending} 条" || bad "没有待处理任务（演示场景未生成）"
[ "${ended:-0}" -gt 0 ] && ok "已结束流程 ${ended} 条" || bad "没有已结束流程"
[ "${cc:-0}" -gt 0 ] && ok "抄送记录 ${cc} 条" || bad "没有抄送记录"

echo
printf '结果：%d 通过，%d 失败\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
