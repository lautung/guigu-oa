# 改名爆炸半径盘点：`com.atguigu` → `com.lautung.oa` 与 `guigu-oa-parent` 目录改名

研究票 #194（map #193）。所有结论基于 2026-11-01 的 main（85ec61e）工作区实测 + 演示栈（guigu-oa compose 栈）只读查询。
grep 口径：`server/guigu-oa-parent` 排除 `target/`；`guigu-oa-parent` 全仓排除 `node_modules/target/.git`。

## 一、`com.atguigu` 类名引用点（Java 源码之外 vs 纯 Java）

总量：241 个文件含 `atguigu`（`grep -rl --exclude-dir=target`），分布 221 `.java` + 17 `.xml` + 3 `.yml`；全部在 `server/guigu-oa-parent` 之内，但 server 之外还有 4 个文件有文字引用（见 A.7）。

### A. 资源/配置/XML 里的引用（容易漏，必须逐处点名）

**A.1 pom.xml（8 个，groupId + coverage 排除路径）**

| 位置 | 内容 |
|---|---|
| `server/guigu-oa-parent/pom.xml:13` | `<groupId>com.atguigu</groupId>` |
| `server/guigu-oa-parent/common/pom.xml:7` | 同上 |
| `server/guigu-oa-parent/common/common-util/pom.xml:7` | 同上 |
| `server/guigu-oa-parent/common/service-util/pom.xml:7,21` | 自身 groupId + 依赖 groupId |
| `server/guigu-oa-parent/common/spring-security/pom.xml:7,21,27` | 自身 groupId + 依赖 groupId ×2 |
| `server/guigu-oa-parent/model/pom.xml:7` | groupId |
| `server/guigu-oa-parent/service-oa/pom.xml:7,29,34,39` | groupId + 依赖 groupId ×3 |
| `server/guigu-oa-parent/coverage-report/pom.xml:7,17,22,27,32` | groupId + 依赖 groupId ×4 |
| `server/guigu-oa-parent/coverage-report/pom.xml:52,53` | JaCoCo 排除是**斜杠路径形式**：`com/atguigu/demo/**`、`com/atguigu/model/**`（不是点号 FQCN，机械替换时两种形态都要顾） |

**A.2 MyBatis mapper XML（8 个，位置本身就在 `src/main/java/com/atguigu/` 包目录下）**

| 文件 | 引用 |
|---|---|
| `service-oa/src/main/java/com/atguigu/auth/mapper/xml/SysMenuMapper.xml:3` | `namespace="com.atguigu.auth.mapper.SysMenuMapper"`；`:6` `type="com.atguigu.model.system.SysMenu"`（**跨模块** FQCN，指向 model 模块） |
| `service-oa/src/main/java/com/atguigu/auth/mapper/xml/SysRoleMenuMapper.xml:3` | namespace |
| `service-oa/src/main/java/com/atguigu/auth/mapper/xml/SysUserMapper.xml:3` | namespace |
| `service-oa/src/main/java/com/atguigu/auth/mapper/xml/SysUserRoleMapper.xml:3` | namespace |
| `service-oa/src/main/java/com/atguigu/process/mapper/xml/OaProcessMapper.xml:3` | namespace；`:24,52,65,87` `resultType="com.atguigu.vo.process.ProcessVo"`（**跨模块**，指向 `model` 模块的 `com.atguigu.vo` 包） |
| `service-oa/src/main/java/com/atguigu/process/mapper/xml/OaProcessRecordMapper.xml:3` | namespace |
| `service-oa/src/main/java/com/atguigu/process/mapper/xml/OaProcessTemplateMapper.xml:3` | namespace |
| `service-oa/src/main/java/com/atguigu/process/mapper/xml/OaProcessTypeMapper.xml:3` | namespace |

注意：这批 XML 在 `src/main/java` 里、由 pom 的 `<resources>` 段按 `src/main/java/**/*.xml` 打进 jar（`service-oa/pom.xml:91-105`，注释「正确打包：同时包含 java 目录下的 XML mapper 和 resources 目录下的所有配置」）。包目录一改，XML 的物理位置随 `git mv` 一起动，`namespace`/`resultType` 内容必须与 Java 侧同批替换。

**A.3 application yml（3 个）**

| 位置 | 内容 |
|---|---|
| `service-oa/src/main/resources/application.yml:53` | `logging.structured.json.customizer: com.atguigu.common.observability.PhoneNumberMaskingCustomizer` —— Spring Boot 启动期反射实例化的 FQCN（实现 `StructuredLoggingJsonMembersCustomizer`，见该类声明），类名不同步改则**首启即失败** |
| `service-oa/src/main/resources/application-dev.yml:12` | `mybatis-plus.mapper-locations: classpath:com/atguigu/*/mapper/xml/*.xml`（斜杠路径形式，跟着包目录走） |
| `service-oa/src/test/resources/application-test.yml:19` | `mybatis-plus.mapper-locations: classpath*:com/atguigu/*/mapper/xml/*.xml`；`:34` 注释提到 `com.atguigu`；`:39` `logging.level.com.atguigu: INFO`（日志级别按包名过滤，ObservabilityContractTest 依赖它） |

**A.4 BPMN（1 个命中）**

| 位置 | 内容 |
|---|---|
| `service-oa/src/main/resources/process/jiaban02.bpmn20.xml:7,12` | `<activiti:taskListener event="create" class="com.atguigu.auth.activiti.MyTaskListener">` |

这是全部 11 个 BPMN 文件里唯一的类引用（见第二节）。且它是**死资源**：`com/atguigu/auth/activiti/` 目录已不存在（`MyTaskListener` 类已删）；`flowable.check-process-definitions: false`（`application-dev.yml:77`）下引擎也不自动部署它。历史文档 `docs/research/Flowable8节点事件挂抄送.md:370` 已把这条记录为「监听器写进 BPMN 的代价」的反面案例。

**A.5 不存在的引用面（核实为空，改名无需顾）**

- 无 logback 配置文件（`find -name 'logback*'` 为空；结构化日志走 Spring Boot `logging.structured` + A.3 的 customizer）。
- 无 `type-aliases-package` / `typeAliasesPackage` 配置（全 server grep 为空——mapper XML 一律用完整 FQCN）。
- springdoc 配置无包名引用：`SpringDocConfig.java` 只有 package 声明；yml 里的 springdoc 段（`application.yml:30`、`application-dev.yml:65`、`application-demo.yml:13`、`application-prod.yml:7`）只有开关与路径。
- 无 `@ComponentScan` 实际使用：`ServiceAuthApplication.java:5` import 了但未标注（死 import），组件扫描来自 `@SpringBootApplication` 所在根包 `com.atguigu`（`ServiceAuthApplication.java:1,8`）——**根包即扫描范围**，改名后 common/model 下的配置类能否被扫到完全取决于三个模块是否都搬进同一新根包。

**A.6 Java 内非 import/package 的字符串/注释（21 处，机械替换时别当纯 import 处理）**

- **行为关键**：`common/service-util/.../mp/MybatisPlusConfig.java:13-17` —— `@MapperScan(basePackages = {"com.atguigu.auth.mapper", "com.atguigu.process.mapper", "com.atguigu.process.attachment"})`。
- **测试反射 / 语句 ID / logger 名**：`OpenApiContractTest.java:16`（`Class.forName("com.atguigu.common.config.knife4j.Knife4jConfig")` 断言不存在）、`WechatCapabilityAbsentTest.java:11-28`（8 个反射缺失断言）、`SlowSqlLoggingInterceptorTest.java:29,30`（MyBatis statement id 字符串 `com.atguigu.auth.mapper.SysUserMapper.selectById/updateStatus`）、`ObservabilityContractTest.java:83,95`（logger 名 `com.atguigu.masking-contract`）、`AttachmentContractTest.java:79`（全限定字段类型）。
- **注释/javadoc**（纯文案，可一并替换）：`ProcessApprovalSupport.java:13`、`PublicationVerifier.java:21`、`ProcessDeploymentEngine.java:6`、`OaProcessService.java:7`、`Approval.java:14,25`、`application-test.yml:34`。

**A.7 server 之外的 `atguigu` 文字引用**

| 位置 | 性质 |
|---|---|
| `scripts/security-scan.mjs:1402,1409` | 源码**文件路径**硬编码（`server/guigu-oa-parent/service-oa/src/main/java/com/atguigu/process/controller/OaProcessTemplateController.java` 等），SEC-CSRF-05 用例 readFileSync 读取——路径不更新则安全套件报错 |
| `README.md:163` | 文字（覆盖率排除描述 `com.atguigu.model.*` / `com.atguigu.demo.*`） |
| `docs/agents/server-modules.md:10,13,14` | 文字（模块地图里的包路径） |
| `docs/research/Flowable8节点事件挂抄送.md:225,240,251,318,370,411,413,414,520`、`docs/research/现有两端能力与路由对照.md:19,20,75,107,169,177` 等 | 历史研究文档内的代码引用/路径，属历史记录，改名时可不追改（与 ADR 惯例一致：历史文档不重写） |

**A.8 纯 Java package/import（机械替换即可）**

其余 ~200 个 `.java`（157 main + 64 test 中的非 A.6 部分）：`package com.atguigu...` / `import com.atguigu...`，无字符串形态。按模块分布：`common` 44 个文件、`model` 34、`service-oa` 161、pom 2（含根 pom 与 coverage-report）。

## 二、Flowable 既有库影响

**2.1 仓库内 BPMN 的类引用：仅 jiaban02，且是死引用**

全部 BPMN（main `resources/process/` 7 个 + `resources/processes/demo-approval.bpmn20.xml` + test `resources/process/` 3 个）grep `class=`/`delegateExpression`：只有 `jiaban02.bpmn20.xml:7,12` 的 `activiti:taskListener class="com.atguigu.auth.activiti.MyTaskListener"`。该类在当前代码中已不存在（无 `com/atguigu/auth/activiti/` 目录）；其余 BPMN 零类引用（candidateUsers/candidateGroups 是用户/组字符串，不算类引用）。测试资源 `src/test/resources/processes/bingjia.zip` 与合同测试 BPMN 也不含 atguigu。

**2.2 部署链路：没有 classpath 自动部署**

- `flowable.check-process-definitions: false`（`application-dev.yml:77`，`application-test.yml:26` 同）——Flowable 不会自动部署 classpath 里的 `processes/` 或 `process/`；`resources/process/` 下 7 个旧 Activiti 时代 BPMN 无任何 Java 消费者（grep `jiaban|qingjia` 仅命中测试 zip 文件名与流程 key 字符串），是纯死资源。
- 正式发布：审批模板发布走 zip 上传 → `ProcessDefinitionStorage` 内容寻址落盘 `processes/sha256/<sha256>.zip`（`ProcessDefinitionStorage.java:75,82,135`；根目录 `guigu.oa.process-definition-dir`，`application.yml:20`，compose 卷 `compose.yaml:86,98`）→ `FlowableProcessDeploymentEngine.deployVerified`（`FlowableProcessDeploymentEngine.java:50-90`，`addZipInputStream` 部署 + SHA-256 前后校验）。
- demo：`DemoApprovalArtifact.java:16,23` 运行时读 classpath `processes/demo-approval.bpmn20.xml` 现打成 zip，经同管道发布（`DemoScenarioSeeder`，`@Profile("demo")`）。`demo-approval.bpmn20.xml` 本身无类引用。
- 切库恢复：`PublishedTemplateRedeployRunner.java:18-38`（开关 `guigu.oa.cutover.redeploy-published=true`）→ `OaProcessTemplateServiceImpl.restorePublished()`（`:215` 起）按发布绑定记录的精确 zip+SHA-256 重部署，缺证据整批失败。

**2.3 既有本地库实证（演示栈 guigu-oa 库，只读查询）**

- `ACT_RE_PROCDEF` 9 条，全部 version 1（demoApproval + api 压测造数上传的 zip）。
- `SELECT COUNT(*) FROM ACT_GE_BYTEARRAY WHERE CONVERT(BYTES_ USING utf8mb4) LIKE '%atguigu%'` → **0**。已部署定义的 BPMN 字节里没有任何 `com.atguigu` 类引用。

**2.4 迁移故事结论**

- **代码层改名对既有库已部署定义无类解析风险**：现网部署链路产生的定义不含 Java 类引用（2.2 + 2.3 实证），包名换掉后旧定义的类解析不受影响；`demo-approval` 重发、模板再发布均走 zip 管道，BPMN 内容不因包名变化。
- **唯一理论风险**是库里存在历史人工上传、BPMN 带 `com.atguigu` 监听器/delegate 的 zip 部署（本地仓库证据无法排除他库）。排查一句 SQL：`SELECT COUNT(*) FROM ACT_GE_BYTEARRAY WHERE CONVERT(BYTES_ USING utf8mb4) LIKE '%com.atguigu%';` 命中即说明有此类定义。处理：用新包名的监听器类重新打包 zip → 走正常发布 → Flowable 对同 key 自动版本递增（`ACT_RE_PROCDEF.VERSION_+1`），绑定指向新 definitionId；旧版本定义留在 `ACT_RE_PROCDEF`/`ACT_GE_BYTEARRAY` 无害（仅当旧版本还有在途实例走到监听器才需要担心，闸门是 `ProcessCutoverSupport.requireNoInProgress`（`ProcessCutoverSupport.java:19-24`）：`oa_process.status=1` 必须为 0）。
- **不需要**为包名改名清 `ACT_RE_PROCDEF`/`ACT_GE_BYTEARRAY`。若要彻底重置引擎，沿用 `server/guigu-oa-parent/sql/flowable8-cutover.md` 的现成配方（DROP 全部 `ACT_%`/`FLW_%` → `--guigu.oa.cutover.redeploy-published=true` 重部署），该配方与包名正交。

## 三、`guigu-oa-parent` 路径引用点

**3.1 CI/自动化（改名必改，否则 CI 红）**

| 位置 | 引用 |
|---|---|
| `.github/workflows/ci.yml:25,26` | paths 过滤 `server/guigu-oa-parent/pom.xml`、`server/guigu-oa-parent/**/pom.xml` |
| `.github/workflows/ci.yml:29` | `mvn -B -f server/guigu-oa-parent/pom.xml test` |
| `.github/workflows/ci.yml:40` | coverage 上报路径 `server/guigu-oa-parent/coverage-report/target/site/jacoco-aggregate` |
| `.github/workflows/ci.yml:44` | 同上产物断言 |
| `.github/workflows/ci.yml:46` | `mvn -B -f server/guigu-oa-parent/pom.xml -DskipTests package` |
| `.github/workflows/ci.yml:48` | `docker build --file server/guigu-oa-parent/service-oa/Dockerfile ... server/guigu-oa-parent` |
| `.github/workflows/regression.yml:36` | **不直接引用**，经 `./scripts/test-round.sh ci-stack` 间接触达 |
| `.github/workflows/security.yml:49` | 同上，经 `./scripts/test-round.sh ci-security` |

**3.2 scripts（间接触达 regression/security 的实际落点）**

| 位置 | 引用 |
|---|---|
| `scripts/verify.sh:9` | `cd "$repo_root/server/guigu-oa-parent"` |
| `scripts/coverage.sh:11` | 同上 |
| `scripts/coverage-summary.mjs:26` | jacoco.csv 相对路径 |
| `scripts/check-contract-assertions.mjs:15` | `join(root, 'server/guigu-oa-parent')` |
| `scripts/security-scan.mjs:221` | application-dev.yml 路径（读 dev JWT secret） |
| `scripts/security-scan.mjs:1402,1409` | 源码文件路径（SEC-CSRF-05，见 A.7——这 2 处同时含 `com/atguigu` 包路径，是「路径 + 包名」双重引用点） |

`scripts/test-round.sh` 自身无路径引用（经 verify.sh 等下沉）。

**3.3 compose / 文档**

| 位置 | 引用 |
|---|---|
| `compose.yaml:69-71` | `context: ./server/guigu-oa-parent` + `dockerfile: service-oa/Dockerfile` |
| `AGENTS.md:35,41,56,66,86` | 路径与启动/测试命令（含 `cd server/guigu-oa-parent && mvn -pl service-oa -am test`） |
| `README.md:122,154` | 启动与测试命令 |
| `docs/agents/server-modules.md:3` | 「`server/guigu-oa-parent/` 是多模块 Maven 工程」 |
| `docs/development/compose-demo.md:43` | Dockerfile 路径 |
| `docs/test/report.md:173`、`docs/test/plan.md:186` | 服务端测试命令 |
| `docs/spec/升级到Spring-Boot-4与Vue-3的依赖规格.md:14`、`docs/spec/面试作品化改造规格.md:56`（后者明言「`guigu-oa-client` / `guigu-oa-parent` 名不变」，历史决策记录）、`docs/prototypes/README-面试作品化草稿.md:127` | 历史规格/草稿 |
| `docs/research/` 7 个文件（行号见 §3.5 明细） | 历史研究文档 |
| `server/guigu-oa-parent/sql/flowable8-cutover.md:16,52,128` | 服务端**仓内自引用**（cd 命令） |

**3.4 明确无引用的面**

- `client/guigu-oa-client`：`package.json`、`vite.config.*`、`playwright*.ts` 全部 0 命中（前端不感知后端目录名）。
- `perf/`：无 `guigu-oa-parent` 引用，只用 compose service 名（`perf/README.md:86-99`）。
- `.github/workflows/regression.yml`、`security.yml`：只引用 `client/guigu-oa-client`。

**3.5 docs/research 明细（历史文档，可不追改）**

`现有两端能力与路由对照.md:5,19,20,75,107,169,177`；`Spring-Boot-4线与后端生态坐标.md:5,12,204`；`Activiti能否跟上Spring-Boot-4.md:5,27,31`；`Flowable-8如何承接Activiti-7存量.md:6,25`；`低成本常驻demo托管方案.md:393`；`Flowable8节点事件挂抄送.md:6`。

## 四、改名后首启行为（既有 `guigu-oa` 库）

**4.1 Flyway：校验不受包名影响**

- 启用方式：`spring-boot-starter-flyway` + `flyway-mysql`（`service-oa/pom.xml:75-80`）；base `application.yml` 与 `application-dev.yml` 均无 `spring.flyway` 段 → 默认 locations `classpath:db/migration`；demo profile 追加 `classpath:db/demo-migration`（`application-demo.yml:2-3`）。
- 迁移内容 0 含 `atguigu`：`src/main/resources/db/migration/`（V1–V107 SQL）与 `src/main/java/db/migration/V104__BackfillProcessStartTime.java` grep 均为空命中。Java 迁移在 `db.migration` 包（不在 `com.atguigu` 树下，`V104` 文件头 `package db.migration;`），包名替换不触及；V104 的 SQL 只碰 `oa_process` 与 `ACT_HI_PROCINST`。
- 结论：目录名/包名替换后，迁移文件位置（`db/migration`）与内容一字不动 → Flyway checksum 校验原样通过，`flyway_schema_history` 不感知包名。

**4.2 Flowable：schema 判定与 #158 语境不变**

- `flowable.database-schema-update: true` + `history-level: full` + `check-process-definitions: false`（`application-dev.yml:73-77`）。已有库首启：引擎照常做 schema 存在性检查、跳过建表、不自动部署任何 BPMN。
- #158 修复 `nullCatalogMeansCurrent: true` 钉在 base `application.yml:8-15` 的 Hikari `data-source-properties`，对所有 profile 与 URL 覆盖生效——**与包名/目录名完全正交**，改名不动它即不复发。
- 已部署定义无类引用（第二节实证），无 classpath 自动部署 → 引擎侧首启无包名敏感点。

**4.3 MyBatis/日志：必须与 Java 同批同步的三处**

- mapper XML 物理位置在包目录下（pom `src/main/java/**/*.xml` 打包，`service-oa/pom.xml:91-105`）→ `git mv` 包目录即随之迁移；`mapper-locations`（`application-dev.yml:12`、`application-test.yml:19`）、`@MapperScan` basePackages（`MybatisPlusConfig.java:14-16`）、mapper XML `namespace`/`resultType`/`type`（8 个 XML）三处必须与 Java 包名**同一提交**内同步，漏一处即启动期 mapper 绑定失败。
- `logging.structured.json.customizer` FQCN（`application.yml:53`）同步改，否则 Spring Boot 启动期反射实例化失败。
- `coverage-report/pom.xml:52,53` 的斜杠路径排除同步改，否则覆盖率分母口径漂移（报告不卡红，但口径失真）。

**4.4 启动发布流程与 jar 打包**

- 无自动部署（4.2）；demo 发布与正式发布都走 zip 管道（2.2），发布绑定 `oa_process_template_publication` 记录 artifactPath/sha256/definitionId，不含包名 → 改名不产生发布绑定失配。
- `processes/demo-approval.bpmn20.xml` 的 classpath 路径不含包名（`DemoApprovalArtifact.java:16`），改名不影响 demo 发布。

**4.5 一句话结论**

既有 `guigu-oa` 库改名后首启：Flyway 校验通过（迁移不含包名）、Flowable 照常复用 `ACT_*` 表（#158 修复正交）、引擎定义无类解析风险；全部风险集中在**仓库内配置与 Java 的同步替换**（mapper 三件套 + customizer FQCN + @MapperScan + coverage 排除 + CI/security 脚本路径），不需要任何数据库清理动作。

## 附：改名落地的检查清单（从本盘点直接导出）

1. Java 包/目录 `com.atguigu` → 新根包（三模块 + test），含 mapper XML 的物理迁移（同一提交）。
2. 8 个 pom groupId + `coverage-report/pom.xml:52,53` 斜杠排除。
3. `application.yml:53`、`application-dev.yml:12`、`application-test.yml:19,34,39`。
4. `MybatisPlusConfig.java:14-16` + 8 个 mapper XML 的 namespace/resultType/type。
5. 测试反射/语句 ID 字符串（A.6 列表）。
6. `jiaban02.bpmn20.xml` 死资源：随包名机械替换即可（它引用的类本就不存在，无运行时影响）；也可另行开票删除整个死资源目录 `resources/process/`（本票不动代码，仅记录）。
7. 路径引用：`ci.yml` 7 处、`compose.yaml:69`、`scripts/` 7 处、`AGENTS.md` 5 处、`README.md` 2 处、`docs/agents/server-modules.md:3`、`docs/development/compose-demo.md:43`、`docs/test/` 2 处、`sql/flowable8-cutover.md` 3 处；历史研究/spec 文档不追改。
8. 数据库动作：无（可选排查 SQL 见 §2.4）。
