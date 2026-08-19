# Flowable 8 切库演练验收清单

对照升级规格 §6 与 #39。可重复执行。脚本：同目录 `flowable8-cutover.sh`。

## 0. 范围

- 丢掉旧引擎运行时（`ACT_%` / `FLW_%`），由 Flowable 8 空库建表。
- 保留 `oa_*` / `sys_*` 账本。已完成的审批实例仍可只读查看。
- 已处理**只**反映切库后的引擎历史，不补写切库前已办完的。
- 不改 HTTP，不让人去点「发布」。已发布模板用启动参数再 deploy 流程 zip。

## 1. 硬前提

```bash
cd 后端代码/guigu-oa-parent/sql
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

在 `service-oa` 目录，确认 `application-dev.yml`：

- `flowable.database-schema-update: true`
- `flowable.history-level: full`
- `flowable.check-process-definitions: false`（不要靠 classpath 样例恢复模板）

启动并再部署已发布模板（不改模板状态）：

```bash
cd 后端代码/guigu-oa-parent/service-oa
mvn -DskipTests install
java -jar target/service-oa.jar --guigu.oa.cutover.redeploy-published=true
```

若 jar 内没有 yml，再加 `--spring.config.additional-location=file:./src/main/resources/`。

- [ ] 启动日志出现 Flowable 建表；库中有 `ACT_*` 与 `FLW_RU_BATCH*`。
- [ ] 日志有「切库再部署已发布审批模板 N 条」，N 等于已发布且带流程 zip 路径的模板数。
- [ ] `oa_process_template.status` 仍为切库前的值（已发布保持 1）。
- [ ] 未把 `process/` 下样例 BPMN 当成已发布模板。

## 4. 合同面验收

登录统一客户端（默认种子：`admin` / `111111`）。

| 面 | 期望 |
|----|------|
| 待处理 | 空（引擎运行时刚建，直到有新待办） |
| 已处理 | 空或不含切库前已办完的；只出现切库后办理的 |
| 已发起 | 仍能看到切库前账本行（已办完的还在） |
| 审批详情（旧实例） | 只读：表单与流转记录来自账本；无通过/拒绝按钮（`isApprove=false`）；问当前任务得空列表可接受 |
| 配置-审批实例查询 | 仍扫 `oa_process` |
| 发起 | 已发布模板可发起 |

- [ ] 待处理为空。
- [ ] 打开一条切库前的已发起/已完成详情：不崩溃、不出现可办理按钮、表单与流转记录仍在。
- [ ] 按已发布模板新发起一条审批，出现在已发起。
- [ ] 处理人在待处理看到该条，通过或拒绝后：待处理消失，已处理出现该条。
- [ ] 已处理仍只有切库后办理的条目，没有把切库前已办完的补进来。
- [ ] 新实例 `startProcessInstanceByKey` 可用（发起成功即证明再 deploy 成功）。

## 5. 演练结论

- 日期 / 环境：
- 硬前提条数：0
- DROP 的引擎表数量：
- 再部署的已发布模板数量：
- 新发起流转是否完整：是 / 否
- 旧实例只读详情是否可用：是 / 否
- 已处理是否未混入切库前历史：是 / 否
