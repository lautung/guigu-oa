# 安全测试：用例与判定口径

来源：[确定安全测试范围、深度与授权边界 #113](https://github.com/lautung/guigu-oa/issues/113) 的 resolution。
执行票：[执行安全测试并留证 #116](https://github.com/lautung/guigu-oa/issues/116)。
缺陷修复闭环：[修复测试发现的全部缺陷并回归 #127](https://github.com/lautung/guigu-oa/issues/127)。
记录规范：[确定测试数据、缺陷记录与报告规范 #122](https://github.com/lautung/guigu-oa/issues/122)。

本文件定「测什么、怎么判」，不写执行结果；执行记录见 `docs/test/execution/`。

## 1. 范围与依据

- **组织框架**：OWASP Top 10 (2021) checklist，每个用例族映射到一类或几类风险。
- **自动扫描**：ZAP 2.17.0 Core 被动 baseline，只访问、不攻击。
- **定向用例**：`scripts/security-scan.mjs`（HTTP 矩阵）+ `client/guigu-oa-client/tests/security/`（真实浏览器 XSS）。
- **身份矩阵**：无 token、`admin`（配置+办事）、`user`（仅办事）、`alice`（申请人）、`bob`（当前处理人/历史处理人）、`carol`（抄送人）。
- **端点底稿**：[盘点接口、鉴权与配置权限矩阵 #110](https://github.com/lautung/guigu-oa/issues/110) 的 56 端点与 35 个配置 gate。

## 2. 授权与安全边界（#113 §4）

- 只打自己的 Compose 栈：`9530`、`8800`；MinIO 只验证预签名 URL 行为，不扫 `9000`/`9001`，不碰 `3306`/`6379`。
- 禁止 DoS、破坏性测试、主动扫描、字典/撞库、分布式尝试。
- 弱口令：每账号失败次数 ≤30，随后验证正确密码仍可登录；仅此一项。
- 资源探测：`limit=100000` 单次只读；上传 50 MiB 一例；不做并发/持续负载。
- 测试数据统一 `sec-` 前缀；执行前打快照、执行后恢复；证据脱敏，token 只留前缀。

## 3. 环境与数据

| 项 | 约定 |
|---|---|
| 被测栈 | `docker compose up -d --build --wait`（`docs/development/compose-demo.md`） |
| 入口 | `DEMO_ORIGIN` 默认 `http://localhost:9530`，API 经 `/dev-api`；匿名面 `API_ORIGIN` 默认 `http://localhost:8800` |
| 快照 | `mysqldump --databases guigu-oa` + `process-definitions` 卷 + `minio-data` 卷 tar（沿用 #112） |
| 恢复 | `down --volumes --remove-orphans` → 起 `mysql`/`minio` → 导入 SQL 与卷 → `up -d --build --wait` |
| 造数 | 全部经真实 API：临时用户、未发布模板、带附件审批、待绑定附件、过期上传凭证；不新增 Flyway 种子 |
| 清理 | 临时用户用 `DELETE` 删除；审批与附件随快照恢复清空 |

## 4. 用例清单（61 条）

### SEC-JWT：令牌完整性与生命周期（A02、A07）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-JWT-01 | 无 token 访问受保护接口被拒 | `body.code=208` |
| SEC-JWT-02 | 伪造 token 被拒 | `body.code=208` |
| SEC-JWT-03 | 篡改 payload（userId→admin）保留签名被拒 | `body.code=208` |
| SEC-JWT-04 | `alg=none` 无签名令牌被拒 | `body.code=208` |
| SEC-JWT-05 | refresh token 当作 access token 使用被拒 | `body.code=208` |
| SEC-JWT-06 | access token 当作 refresh token 使用被拒 | `body.code=208` |
| SEC-JWT-07 | 登出后 access token 立即失效 | `body.code=208` |
| SEC-JWT-08 | 停用账号的存量 token 被拒 | `body.code=209` |
| SEC-JWT-09 | 过期 token 被拒（用 dev 密钥签一个过期令牌） | `body.code=208` |
| SEC-JWT-10 | 其他密钥签名的 token 被拒 | `body.code=208` |

### SEC-IDOR：跨用户对象访问（A01）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-IDOR-01 | 非成员读取他人审批详情被拒 | `body.code=403` |
| SEC-IDOR-02 | 非成员处理他人审批被拒 | `body.code=403` |
| SEC-IDOR-03 | 非成员读取审批附件列表被拒 | `body.code=205 或 403（拒绝）` |
| SEC-IDOR-04 | 非成员签发他人附件下载凭证被拒 | `body.code=205 或 403（拒绝）` |
| SEC-IDOR-05 | 他人草稿附件列表按所有者隔离 | 返回空列表 |
| SEC-IDOR-06 | 他人待绑定附件不可删除 | `body.code=205 或 403（拒绝）` |
| SEC-IDOR-07 | 办事侧模板读只返回已发布模板 | `data=null` |
| SEC-IDOR-08 | 抄送人不能处理审批 | `body.code=403` |
| SEC-IDOR-09 | 他人未完成上传的附件不能代为完成 | `body.code=205 或 403（拒绝）` |

### SEC-MEM：成员资格能力矩阵（A01，ADR 0011）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-MEM-01 | 申请人：可读、可补传、不可处理 | `isApprove=false, canUpload=true`，`approve→403` |
| SEC-MEM-02 | 当前处理人：可读、可上传、可处理 | `isApprove=true, canUpload=true` |
| SEC-MEM-03 | 抄送人：可读、只读 | `isApprove=false, canUpload=false` |
| SEC-MEM-04 | 历史处理人：审批后可读、不可再处理 | `isApprove=false`，再次 `approve→403` |
| SEC-MEM-05 | 非成员：三类能力全部拒绝 | show/attachments/approve 全部拒绝 |

### SEC-GATE：配置权限 gate（A01、A05，ADR 0014）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-GATE-01 | 35 个配置端点对无配置权限账号返回 205 | 每个端点 `body.code=205`；写端点发 JSR303 良构体（校验先于 gate 合同下走 `@PreAuthorize` 拒绝路径，#184） |
| SEC-GATE-02 | 伪造 gate 参数/请求头不能绕过配置权限 | `body.code=205` |
| SEC-GATE-03 | 有配置权限账号可读配置端点（gate 未过度拒绝） | 6 个只读端点 `code=200` |

### SEC-UPLOAD：上传校验与越权（A08，ADR 0002/0011）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-UPLOAD-01 | 超过 50 MiB 的附件登记被拒 | `code=201` 且提示大小限制 |
| SEC-UPLOAD-02 | 预签名 PUT 的 Content-Type 与登记不一致时不能完成登记 | PUT 被拒或 `complete→201` |
| SEC-UPLOAD-03 | 对象实际大小与登记不一致时不能完成登记 | `complete→201` |
| SEC-UPLOAD-04 | 当前处理人可向审批补传附件 | `code=200` |
| SEC-UPLOAD-05 | 抄送人不能向审批补传附件 | `code=205 或 403（拒绝）` |
| SEC-UPLOAD-06 | 非成员不能向审批上传附件 | `code=205 或 403（拒绝）` |
| SEC-UPLOAD-07 | 流程模板上传拒绝非 zip 后缀 | `code=201` 且提示必须是 zip |
| SEC-UPLOAD-08 | 流程 zip 文件名穿越路径被消解 | 路径落在 `processes/sha256/` 下，key 不含路径分隔 |
| SEC-UPLOAD-09 | 未完成的上传凭证在过期后不能完成登记 | `complete→201`（凭证已过期） |

### SEC-CSRF：跨源与方法语义（A01、A05）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-CSRF-01 | 普通接口不回显恶意 Origin 的 CORS 头 | 无 `Access-Control-Allow-Origin` |
| SEC-CSRF-02 | 审批接口不回显恶意 Origin 的 CORS 头 | 无 `Access-Control-Allow-Origin` |
| SEC-CSRF-03 | 跨源预检不授予带 token 头的写请求 | 预检不返回允许 token 头的 ACAO |
| SEC-CSRF-04 | `GET updateStatus` 不应有写副作用 | 状态不变；写只由 POST/PUT 触发 |
| SEC-CSRF-05 | `GET publish` 不应是写操作 | 发布只由 POST/PUT 触发 |

### SEC-BRUTE：认证失败处理（A07）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-BRUTE-01 | 不存在用户与密码错误返回同一错误码与文案 | 均 `code=208` 且 message 相同 |
| SEC-BRUTE-02 | 30 次失败后正确密码不能直接登录 | 应有锁定/限流/退避 |

### SEC-LEAK：敏感信息暴露（A05、A09）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-LEAK-01 | 用户分页不返回密码哈希 | `records` 不含 `password` |
| SEC-LEAK-02 | 用户详情不返回密码哈希 | `data` 不含 `password` |
| SEC-LEAK-03 | OpenAPI 文档不匿名可读 | 匿名不可读 |
| SEC-LEAK-04 | Swagger UI 不匿名可读 | 匿名不可读 |
| SEC-LEAK-05 | 其他 actuator 端点匿名不可读 | `code=208` 或 HTTP 401/403 |
| SEC-LEAK-06 | 抄送人选项接口不暴露账号名 | 每条只含 `id` 与显示名 `name` |
| SEC-LEAK-07 | 异常响应不返回堆栈或内部信息 | 仅通用错误文案 |
| SEC-LEAK-08 | 服务端日志不含密码或完整 token | 无明文命中 |
| SEC-LEAK-09 | 客户端响应头不暴露 nginx 精确版本 | 无版本号 |

### SEC-HDR：客户端安全响应头（A05、A06）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-HDR-01 | 客户端 HTML 响应带强制 CSP | `default-src 'self'`、`script-src 'self'`、`frame-ancestors 'none'`，`script-src` 不含 `unsafe-*`；`style-src` 只放宽到 `'self' 'unsafe-inline'` |
| SEC-HDR-02 | 客户端 HTML 响应带 `X-Frame-Options` | 每个页面都是 `DENY` |
| SEC-HDR-03 | HTML 与静态资源带 `X-Content-Type-Options` | 文档与构建产物都是 `nosniff` |
| SEC-HDR-04 | CSP 的 `connect-src` 放行预签名上传源 | 含 `'self'` 与 http(s) 源，占位符已展开 |
| SEC-HDR-05 | CSP 生效时浏览器无安全策略违规 | 登录、办事列表/详情、模板列表与表单设计器均无 `securitypolicyviolation` |

### SEC-PAGE：分页资源上限（A05）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-PAGE-01 | 用户分页拒绝超大 limit | `body.code=201`（ADR 0024：拒绝，不静默截断） |
| SEC-PAGE-02 | 审批待处理分页拒绝超大 limit | `body.code=201`（ADR 0024：拒绝，不静默截断） |
| SEC-PAGE-03 | 负数/零分页参数被安全处理 | 不返回 5xx |

### SEC-XSS：存储型 XSS（A03）

| ID | 用例 | 期望 |
|---|---|---|
| SEC-XSS-01 | 审批表单字段存储型 XSS 不执行 | 列表与详情均不执行（`window.__secXss` 为空） |

## 5. 判定口径

- **通过**：实际行为满足期望。
- **失败**：实际偏离期望，按 #113 §5 分级并登记缺陷。
- **阻塞**：环境不可用或前置缺失，记录原因与依赖票（本轮无阻塞）。
- **跳过**：只允许「读不到 dev JWT secret」这类环境性缺口，需在结果中注明（本轮无跳过）。
- 安全基线要求：定向用例全部执行、ZAP 被动报告归档、失败项全部入账；没有「S1/S2 必须为零才通过」的门槛——本轮的目的就是找缺陷。

## 6. 严重级别（#113 §5 / #122 §3）

| 级别 | 定义 |
|---|---|
| S1 | 认证/授权完全绕过、跨用户读写、可执行并可能窃取 token 的存储型 XSS |
| S2 | 权限模型绕过、敏感数据泄露（密码哈希/密钥）、未授权写、预签名越权 |
| S3 | 缺限流/锁定、GET 写操作、上传校验缺口、安全硬化缺失 |
| S4 | 信息暴露（Swagger/版本/Actuator/CORS）、低影响最佳实践 |

一缺陷一 GitHub issue，标题 `[安全][Sx] …`，标签 `bug` + `severity:Sx`；状态 `open → fixed（defect:fixed）→ verified（closed）`。

## 7. 证据与复跑

```bash
# 定向矩阵（--only 可选，族或具体用例 ID）
node scripts/security-scan.mjs --out docs/test/evidence/<轮次>

# ZAP 2.17.0 Core 被动 baseline（先解压到 /tmp/zap/ZAP_2.17.0）
node scripts/zap-baseline.mjs --out docs/test/evidence/<轮次>

# 存储型 XSS（真实浏览器）
cd client/guigu-oa-client && npm run test:security
```

- 入仓：`security-results.json`、`security-summary.md`、`environment.md`、`zap-summary.md`、`zap-alerts.json`、`xss-report.json`、`xss-executed.png`；单文件 ≤100KB、目录 ≤5MB。
- 不入仓：完整 ZAP HTML/JSON、Playwright trace/视频；路径写进执行记录。
- 修复后回归：同一脚本、同一快照复跑，差异作为 #127 的 `verified` 证据。

## 8. 已知局限

- ZAP 只做被动：不发现需要主动探测的注入点；定向用例覆盖已知风险面，不替代渗透测试。
- 授权范围仅限本机 Compose；结论不外推到公网/生产。
- `sysUser`/`sysRole` 页面级授予允许持有者给自己或角色加任意配置页（ADR 0014）是**设计取舍**，记为 accepted risk，不登记缺陷；「没有 `sysUser` 的人绕过 gate」已由 SEC-GATE 覆盖。
- 附件类越权在本轮统一返回 205（`没有操作权限`），审批详情用 403；断言接受两者，报告把编码不一致列为观察项而非缺陷。
- 弱口令只做 30 次单账号尝试，不评估分布式撞库。
