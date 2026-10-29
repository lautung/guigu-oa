# GET 写操作人工复核（#138）

- 被测版本：`09799e995c59f6a188cb9e3acab2a1df6db22f83`（`fix: serve status toggles and template publishing only over write methods (#138)`）
- 栈：本机 Compose 演示栈（`MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose up -d --build --wait`，5 容器 healthy）
- 入口：`http://localhost:9530/dev-api`
- 账号：`admin`，密码 `111111`；目标用户是扫描临时账号 `sec-b-*`，目标模板是种子「请假申请」（id 1，已发布）

## 命令

```bash
ADMIN=$(curl -s -X POST http://localhost:9530/dev-api/admin/system/index/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"111111"}' | jq -r '.data.token')

# 停用/启用用户：旧 GET 不再写，新 PUT 生效
curl -s "http://localhost:9530/dev-api/admin/system/sysUser/get/$USER_ID" -H "token: $ADMIN"
curl -s "http://localhost:9530/dev-api/admin/system/sysUser/updateStatus/$USER_ID/0" -H "token: $ADMIN"
curl -s "http://localhost:9530/dev-api/admin/system/sysUser/get/$USER_ID" -H "token: $ADMIN"
curl -s -X PUT "http://localhost:9530/dev-api/admin/system/sysUser/updateStatus/$USER_ID/0" -H "token: $ADMIN"
curl -s "http://localhost:9530/dev-api/admin/system/sysUser/get/$USER_ID" -H "token: $ADMIN"
curl -s -X PUT "http://localhost:9530/dev-api/admin/system/sysUser/updateStatus/$USER_ID/1" -H "token: $ADMIN"

# 发布模板：旧 GET 不再发布，新 POST 才到达 service
curl -s "http://localhost:9530/dev-api/admin/process/processTemplate/get/1" -H "token: $ADMIN"
curl -s "http://localhost:9530/dev-api/admin/process/processTemplate/publish/1" -H "token: $ADMIN"
curl -s "http://localhost:9530/dev-api/admin/process/processTemplate/get/1" -H "token: $ADMIN"
curl -s -X POST "http://localhost:9530/dev-api/admin/process/processTemplate/publish/1" -H "token: $ADMIN"
```

## 结果

| 动作 | HTTP | body.code | message | 观察 |
|---|---|---|---|---|
| `GET /sysUser/get/{id}`（前） | 200 | 200 | 成功 | `data.status=1` |
| `GET /sysUser/updateStatus/{id}/0`（旧入口） | 200 | 201 | 执行全局异常处理... | 没有处理器，方法不支持被收成失败信封 |
| `GET /sysUser/get/{id}`（后） | 200 | 200 | 成功 | `data.status=1`，状态未变 |
| `PUT /sysUser/updateStatus/{id}/0` | 200 | 200 | 成功 | 写入口 |
| `GET /sysUser/get/{id}`（PUT 后） | 200 | 200 | 成功 | `data.status=0`，写生效 |
| `PUT /sysUser/updateStatus/{id}/1` | 200 | 200 | 成功 | 复原 |
| `GET /processTemplate/get/1`（前） | 200 | 200 | 成功 | `data.status=1`（已发布） |
| `GET /processTemplate/publish/1`（旧入口） | 200 | 201 | 执行全局异常处理... | 落到同前缀分页读路由并在参数转换失败，未发布 |
| `GET /processTemplate/get/1`（后） | 200 | 200 | 成功 | `data.status=1`，仍为原状 |
| `POST /processTemplate/publish/1` | 200 | 201 | 已发布的审批模板不能再发布 | POST 到达 service，按既有规则拒绝重复发布 |

## 补充：浏览器发起的真实发布

`npx playwright test --config tests/e2e-stack/playwright.config.ts p1-1-template-publish.spec.ts p0-1-auth.spec.ts` → **7 通过**。

- `p1-1-template-publish` 通过 UI 创建未发布模板、上传流程 zip、点「发布」，随后确认模板变成已发布且编辑/删除/发布按钮消失。这条覆盖 `POST /publish/{id}` 的成功路径。
- `p0-1-auth` 里「停用的用户不能登录，恢复后可以」通过 `Oracle.setUserStatus` 走新的 `PUT /updateStatus`。

## 结论

- 两个写操作的旧 GET 入口都不再产生写效果：用户状态保持 1，模板状态保持 1；返回的是失败信封（`code=201`），不是成功。
- 新的写方法生效：`PUT updateStatus` 把状态改成 0，`POST publish` 到达 service 并按既有规则拒绝重复发布；UI 的完整发布路径通过 e2e-stack。
- `SEC-CSRF-04/05` 在复跑中转为通过；`SEC-CSRF-02/03`（CORS）仍失败，属 #141，与本缺陷无关。
- 复跑后 `./scripts/compose-baseline-check.sh` 为 20 通过、0 失败（扫描临时账号与未发布模板已清理）。
