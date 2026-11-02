# Flowable 8 切库演练验收清单

对照升级规格 §6 与 #39。可重复执行。脚本：同目录 `flowable8-cutover.sh`。

## 0. 范围

- 丢掉旧引擎运行时（`ACT_%` / `FLW_%`），由 Flowable 8 空库建表。
- 保留 `oa_*` / `sys_*` 账本。已完成的审批实例仍可只读查看。
- 已处理**只**反映切库后的引擎历史，不补写切库前已办完的。
- 不改 HTTP，不让人去点「发布」。切库恢复按后端发布绑定里的精确 zip 与 SHA-256 执行，不猜 latest version。
- 恢复缺任意证据时，整批失败并中止启动；旧模板保持已发布、只读和不可发起。

## 1. 硬前提

```bash
cd server/lautung-oa-parent/sql
./flowable8-cutover.sh --dry-run
```

- [ ] `oa_process.status=1` 为 0 条。否则演练停止，先在产品里办完或拒绝。
- [ ] 不把审批中行改成驳回（`-1`），不删已完成账本。

## 2. 可选备份

```bash
./flowable8-cutover.sh --backup-dir /tmp/guigu-oa-engine-backup
```

- [ ] 备份文件不再被 Flowable 连接。

不需要备份时直接：

```bash
./flowable8-cutover.sh
```

- [ ] 已 DROP 全部 `ACT_%`（含 Activiti 独有表）和 `FLW_%`。
- [ ] `oa_process` / `oa_process_record` / `oa_process_template` / `sys_*` 行数未变。

## 3. 以 Flowable 8 启动并再部署

先按第 4 节完成所有缺证模板的人工核实。在 `lautung-oa-server` 目录，确认 `application-dev.yml`：

- `flowable.database-schema-update: true`
- `flowable.history-level: full`
- `flowable.check-process-definitions: false`（不要靠 classpath 样例恢复模板）
- `GUIGU_OA_PROCESS_DEFINITION_DIR` 指向持久保存流程 zip 的目录；数据库中的流程路径相对该目录解析。容器运行时要把这个目录挂到可写且跨重建保留的 volume。

启动并再部署已发布模板（不改模板状态）：

```bash
cd server/lautung-oa-parent/lautung-oa-server
mvn -DskipTests install
java -jar target/lautung-oa-server.jar --guigu.oa.cutover.redeploy-published=true
```

若 jar 内没有 yml，再加 `--spring.config.additional-location=file:./src/main/resources/`。

- [ ] 启动日志出现 Flowable 建表；库中有 `ACT_*` 与 `FLW_RU_BATCH*`。
- [ ] 日志有「切库再部署已发布审批模板 N 条」；N 是本次新建或补齐的精确绑定数。已经存在且完全匹配的定义不会重复部署。
- [ ] `oa_process_template.status` 仍为切库前的值（已发布保持 1）。
- [ ] 每个已发布模板都有匹配原产物 SHA-256、BPMN key、Flowable definition ID 和 deployment ID 的后台绑定。
- [ ] 未把 `process/` 或 `processes/` 下的当前样例 BPMN 当成原发布内容。

## 4. 旧模板人工核实与恢复

V4 不会为历史 `status=1` 行自动造发布绑定。只凭数据库当前路径、同名文件、BPMN key 唯一或 Flowable 的 latest version 都不能证明历史发布内容。证据不足的模板保持已发布、只读、不可发起；不得改回未发布来绕过检查。

### 4.1 找出待核实模板

先停止应用写流量并备份业务库、Flowable 库和 `GUIGU_OA_PROCESS_DEFINITION_DIR`。记录数据库与归档目录备份位置，再执行：

```sql
SELECT t.id, t.name, t.process_definition_key, t.process_definition_path, t.status
FROM oa_process_template t
LEFT JOIN oa_process_template_publication p ON p.process_template_id = t.id
WHERE t.status = 1 AND (p.id IS NULL OR p.artifact_sha256 IS NULL OR p.artifact_path IS NULL);
```

对每个结果逐条核实。优先使用与原发布操作对应、可追溯且不可变的 release zip、签名清单/可信 SHA-256、发布审计记录和原 Flowable 部署记录。必须能证明该 zip 就是该模板当时发布的内容，且压缩包中的唯一可执行流程定义 key 与 `process_definition_key` 完全相同。不能证明时停止该模板的恢复；不要从源码样例、当前 `latestVersion`、同名 zip 或唯一 key 推断。

### 4.2 放置经核实的 zip 并补证据

以下命令在能访问可信归档和配置目录的运维主机上执行。先把变量指向**已独立核实**的归档；不能用正在运行实例读出的 SHA 作为唯一可信证据：

```bash
export VERIFIED_ZIP=/secure/releases/approval-template-123.zip
export PROCESS_DEFINITION_DIR=/var/lib/guigu-oa/process-definitions
sha256sum "$VERIFIED_ZIP"
unzip -Z1 "$VERIFIED_ZIP"
```

将 `sha256sum` 输出与可信发布清单核对；检查 zip 内 BPMN 的唯一可执行 `<process id="...">` 与第 4.1 节的 key 完全相同。然后将原始字节保存到内容寻址目录。已有相同目标时必须比较字节，不得覆盖：

```bash
digest=$(sha256sum "$VERIFIED_ZIP" | awk '{print $1}')
relative_path="processes/sha256/${digest}.zip"
target="$PROCESS_DEFINITION_DIR/$relative_path"
mkdir -p "$(dirname "$target")"
if [ -e "$target" ]; then
  cmp -s "$VERIFIED_ZIP" "$target" || { echo "目标已存在但内容不同，停止" >&2; exit 1; }
else
  cp -n "$VERIFIED_ZIP" "$target"
fi
chmod 0444 "$target"
test "$(sha256sum "$target" | awk '{print $1}')" = "$digest"
```

对每个已核实模板，把摘要、相对路径和从模板行读取的确切 key 写入后台绑定。历史 Flowable ID 在新引擎里不存在时保持 `NULL`，绝不能填写猜测值；整批恢复会校验 zip 后部署，并在同一事务里写入新 ID：

```sql
INSERT INTO oa_process_template_publication (
  process_template_id, artifact_path, artifact_sha256, process_definition_key,
  deployment_id, process_definition_id
)
SELECT id, 'processes/sha256/<64位摘要>.zip', '<64位摘要>', process_definition_key, NULL, NULL
FROM oa_process_template
WHERE id = <模板ID> AND status = 1;
```

把尖括号占位符替换为已核对的字面值后再执行，并确认影响 1 行。不要改模板状态或 key。若已经存在发布绑定但字段冲突，先保留原行和备份，按审计记录查明原因；不要直接覆盖证据。

### 4.3 全批恢复、失败处理与核实

恢复接口检查所有 `status=1` 模板。即使某一模板缺 zip、hash 不匹配、key 不一致或当前定义绑定损坏，也会令整个事务回滚并使应用启动失败。先为所有历史模板完成核实，再以恢复开关启动：

```bash
cd server/lautung-oa-parent/lautung-oa-server
java -jar target/lautung-oa-server.jar --guigu.oa.cutover.redeploy-published=true
```

成功后确认每个绑定都能精确关联 Flowable 定义与 deployment：

```sql
SELECT p.process_template_id, p.artifact_path, p.artifact_sha256,
       p.process_definition_key, p.deployment_id, d.ID_ AS actual_definition_id,
       d.KEY_ AS actual_key, d.DEPLOYMENT_ID_ AS actual_deployment_id
FROM oa_process_template_publication p
LEFT JOIN ACT_RE_PROCDEF d ON d.ID_ = p.process_definition_id
ORDER BY p.process_template_id;
```

每个已发布模板都应有一行，`actual_definition_id`、`actual_key`、`actual_deployment_id` 分别与绑定中的 ID、key、deployment ID 一致；再确认 `status=1` 未变化、前端只列出已核实可发起项，并对每个模板实际发起一条演练审批。随后移除恢复开关，按常规方式启动应用。

若失败，保留启动日志、数据库错误和原始 zip；整批新部署及 binding 更新应已回滚。修复经核实的 archive 或 binding 后重跑。不要删除内容寻址 zip，不要把模板退回未发布，不要人工写 Flowable 新 ID，也不要用重复发布来修复。

## 5. 合同面验收

登录统一客户端（默认种子：`admin` / `111111`）。

| 面 | 期望 |
|----|------|
| 待处理 | 空（引擎运行时刚建，直到有新待办） |
| 已处理 | 空或不含切库前已办完的；只出现切库后办理的 |
| 已发起 | 仍能看到切库前账本行（已办完的还在） |
| 审批详情（旧实例） | 只读：表单与流转记录来自账本；无通过/拒绝按钮（`isApprove=false`）；问当前任务得空列表可接受 |
| 配置-审批实例查询 | 仍扫 `oa_process` |
| 发起 | 仅有可靠发布绑定且当前精确 definition 可用的模板可发起；不可核实旧模板显示说明并拒绝直接 URL 启动 |

- [ ] 待处理为空。
- [ ] 打开一条切库前的已发起/已完成详情：不崩溃、不出现可办理按钮、表单与流转记录仍在。
- [ ] 按已发布模板新发起一条审批，出现在已发起。
- [ ] 处理人在待处理看到该条，通过或拒绝后：待处理消失，已处理出现该条。
- [ ] 已处理仍只有切库后办理的条目，没有把切库前已办完的补进来。
- [ ] 新实例使用已验证绑定的精确 process definition ID 启动；同 key 多模板时分别启动并确认任务步骤没有串版。

## 6. 演练结论

- 日期 / 环境：
- 硬前提条数：0
- DROP 的引擎表数量：
- 再部署的已发布模板数量：
- 新发起流转是否完整：是 / 否
- 旧实例只读详情是否可用：是 / 否
- 已处理是否未混入切库前历史：是 / 否
- 每个历史已发布模板的 artifact/hash/key 证据来源：
- 恢复失败/修复重试与重复执行是否符合预期：
