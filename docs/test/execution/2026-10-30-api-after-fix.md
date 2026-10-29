# 接口测试 after-fix 轮执行记录（#127 · #145/#146 修复回归）

- 日期：2026-10-30
- 执行票：[修复测试发现的全部缺陷并回归 #127](https://github.com/lautung/guigu-oa/issues/127)
- 被回归缺陷：[#145](https://github.com/lautung/guigu-oa/issues/145)（S4，错误一律 HTTP 200）、
  [#146](https://github.com/lautung/guigu-oa/issues/146)（S3，删角色授予不回收）
- 口径与用例集：[基线轮记录](2026-10-29-api.md)（#123），同一命令、同一栈复跑
- 修复提交：#146 `9cc0918`、#145 `70cb454`（提交后按 #122 §4 以本轮证据佐证）

## 1. 环境与命令

```sh
docker compose down --volumes --remove-orphans
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose build service-oa
docker compose up -d --wait
./scripts/compose-baseline-check.sh          # 26/26 PASS
cd client/guigu-oa-client
DEMO_ORIGIN=http://localhost:9530 npm run test:api   # 连续两轮
```

服务端 `mvn -pl service-oa -am test`（333 绿）与客户端 `npm run verify`
（496 单测绿）分别在修复提交前跑过。

## 2. 结果

| 轮次 | 通过 | 失败 | 跳过 | 耗时 |
|---|---|---|---|---|
| after-fix-1（干净栈） | 66 | 0 | 0 | 14.4s |
| after-fix-2（同栈续跑） | 66 | 0 | 0 | 13.2s |

用例总数 65 → 66：f-http-semantics 组从 6 例改写为 7 例（见 §3），
其余用例未动。两轮结果一致，0 重试。

## 3. 缺陷用例翻红转绿对照

| 缺陷 | 基线轮断言（缺陷行为） | 本轮断言（修复后契约） | 结果 |
|---|---|---|---|
| #145 F1 | 登录失败 HTTP 200 + 208 | HTTP 401 + code 208 | ✅ |
| #145 F2 | 未知路径 HTTP 200 + 201「执行全局异常处理...」 | HTTP 404 + 201「请求的资源不存在」 | ✅ |
| #145 F3 | GET publish/1 被吞成 200 + 201 | 改为真 405 用例：GET doAssign → HTTP 405 + 201；GET publish/1 实为分页路由参数类型不匹配，单列 F7 断言 200 + 201 不变 | ✅ |
| #145 F4 | GET updateStatus 被吞成 200 + 201 | HTTP 405 + 201 | ✅ |
| #145 F5 | 无 token HTTP 200 + 208 | HTTP 401 + 208 | ✅ |
| #145 F6 | 匿名路径错误方法 HTTP 403 + 空体 | HTTP 401 + code 208（统一响应体） | ✅ |
| #146 | 删角色后成员页仍 200 | 删角色后原授予页 205 且 /info configMenus 为空 | ✅ |

#143/#144 的已改写用例（B5、B8）继续通过，未受 #145 状态码调整影响：
两个用例只断言 `body.code`，信封仍是权威契约。

## 4. 契约变化说明（#145）

- 认证失败（无 token / 坏 token / 登录失败 / 锁定 / 停用）→ HTTP 401 + `body.code` 208/209；
  匿名路径上的错误方法不再出现 403 + 空体；
- 容器级错误 → 真实状态码 404/405 + 业务信封（code 201），message 指明真实原因；
- 业务失败仍是 HTTP 200 + `body.code`（201/205 等），全部既有业务断言不受影响；
- 服务端请求级日志/监控此后可按状态码统计错误率。

## 5. 证据

目录：`docs/test/evidence/2026-10-29-api-after-fix/`

| 文件 | 内容 | 生成命令 |
|---|---|---|
| `report.json` | Playwright JSON 报告（92 KB，第二轮） | `DEMO_ORIGIN=http://localhost:9530 npm run test:api` |
| `junit.xml` | JUnit XML（12 KB） | 同上 |
| `README.md` | 目录索引与复跑说明 | — |

## 6. 复跑方式

见 §1；干净栈为必要前提——本轮曾先在压测造数卷（10k 实例残留）上试跑，
D1（alice 已发起列表断言空）与 E5 因数据态失败，与修复无关；重置干净栈后两轮全绿。

## 7. 遗留

- `sysMenu/doAssign`「旧非配置 id 静默跳过」仍需 legacy 数据构造，维持基线轮的未覆盖结论；
- 404/405 的 message 文案（「请求的资源不存在」「请求方法不支持」）已入契约，
  后续调整需同步 f-http-semantics 与 `HttpErrorSemanticsContractTest`。
