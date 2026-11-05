# 审计收成一个 module，操作日志注解采集、登录日志由 security 端口供给

蓝图把操作日志与登录日志定为 P0 的系统管理·日志管理。现状是：登录审计只有失败/锁定/解锁三个事件写应用日志（`LoginAudit`/`Slf4jLoginAudit`，ADR 0023 明确留了「将来换实现、登录路径不动」的口子），成功登录没有事件；操作日志完全不存在；仓库没有 AOP starter、没有 async、没有 Excel 依赖。若依的 `sys_oper_log`/`sys_logininfor` 可作字段参照，但它的 `${params.dataScope}`（ADR 0053 已否决）、ip2region 归属地、`operator_type`、导出都不照搬。

决定：

- **审计收成一个 module** `lautung-oa-audit`（依赖 framework + model + security）。`@OperLog` 注解放 `framework`（所有域模块已依赖，零新边）；切面/落库/查询/controller 在 audit。登录日志的**写端口留在 security**（安全模块不依赖上层，无环），DB 实现由 audit 提供——ADR 0023 的承诺兑现，登录路径不动。
- **采集是注解 + AOP**：`@OperLog(title, businessType)` 挂在后台 controller 写方法上。切面 `@Order` 压在方法安全**之内**，只记真正进入方法体的调用，所以 205 拒绝与 `@Valid` 失败天然不产生日志行。成功判定看信封：正常返回且 `Result.code == 200` 为成功；非 200 或抛异常为失败，失败行**同样记参数**（排障要看到尝试了什么）。
- **同步落库**：`after-returning`/`after-throwing` 里直接写，写失败 catch + WARN，不影响响应；不引 async 基础设施。业务事务在 controller 返回时已提交，「响应成功即日志在库」。
- **记录范围**：后台写动作（`/admin/system/*` 及后续后台模块的 POST/PUT/DELETE）全记，并用一条守卫测试钉住（后台 controller 的写方法必须挂注解，漏挂即红）。办事与审批的写动作**不重复记**（`oa_process_record` 已是动作账本，ADR 0049）；登录/续期/退出不进操作日志。日志管理自身的删除/清空也记一行。
- **字段集**：`title`（「用户管理·新增用户」）、`business_type`（新增/修改/删除/授权/发布/清空/解锁/其他）、`method`（`类#方法`）、`request_method`、`oper_id`、`oper_name`、`dept_id`、`dept_name`、`oper_url`、`oper_ip`、`oper_param`、`result_code`、`result_message`、`status`（0 成功 / 1 失败）、`error_msg`、`cost_time`、`create_time`（公共字段，不另设 `oper_time`）。**不落完整响应 `data`**（只记 code + message）；不记 `operator_type`（没有第二种操作人）与 IP 归属地（不引 ip2region）。`title`/`oper_name`/`dept_name` 全是**写入时快照**，不 join `sys_menu`/`sys_user`：菜单改名、用户调部门都不重写历史行。
- **落库脱敏是字段级的，ADR 0039 仍只管 stdout**：序列化前按字段名黑名单（`password`/`oldPassword`/`newPassword`/`confirmPassword`/`token`/`refreshToken`/`secret`/`authorization`，大小写不敏感）替换为 `"***"`，整串再过 `PhoneNumberMasker`；截断 `oper_param`/`error_msg` 2000 字符、`result_message` 500，超出加截断标记。登录日志的 `username` 存**原文**、响应不二次打码：管理员排查要看到被撞的账号，手机号形态的用户名在后台可见全文是已接受的边界。
- **登录日志的端口收成一个事件**：`record(LoginEvent)`（类型 + username + source + userAgent + msg），DB 实现与现有 `Slf4jLoginAudit` 用 composite 组合——库是查询真相，日志行是 DB 故障期的兜底轨迹。`status` 四值 **0 成功 / 1 失败 / 2 锁定 / 3 解锁**（GLOSSARY 要求四类都能查）。新增两个调用点：成功登录记 0；停用账号记 1（msg 账号已停用；它在 ADR 0023 里不算猜密码，但审计要留）。**被锁定挡下的尝试不落库**，只留应用日志——否则一个攻击者就能把审计表变成写放大面，这正是 ADR 0023 否决落库的理由。`browser`/`os` 用自研正则表解析（Chrome/Edge/Firefox/Safari × Windows/macOS/Linux/iOS/Android），存两列，**不存 UA 原串**；`user_id`/`dept_id` 写入时按 username 解析快照，查不到留空。
- **解锁只解账号维度**：清 `login:attempt:account:<name>:*` 三个键并写一条 `status=3` 行。来源维度不提供页面解锁（它是抗撞库阈值，人工解除要能看来源，留给 Redis 手工）。锁定的唯一真相仍在 Redis（ADR 0023 否决过写 `sys_user`），解锁就是清键 + 审计行，不改用户状态。解锁在操作日志里另记一行（「谁点了解锁」），登录日志那行记「该账号被解」——两表用途不同，**双写**。
- **保留期：不做**。不自动归档、不定时清理（自用、无期限；定时任务是 P2）。只提供按 id 批量删除 + 清空全部，都要按钮点权限、清空要二次确认；不提供「只清 N 天前」。日志表**物理删除**、无 `is_deleted`：逻辑删除会让「清空」变成假清空，每条读还要带存活过滤；因此不继承 `BaseEntity`，只有 `create_time` 由 `CommonFieldsMetaObjectHandler` 按字段名填充。
- **查询面**：分页 1..500（ADR 0024），`create_time DESC, id DESC` 稳定排序（ADR 0045 的纪律）。操作日志有详情（弹窗看 param/result/error），登录日志一行即全貌、无详情。**不做导出**（蓝图能力清单没有，且要为自用新引 Excel 依赖）。数据范围只接收 #210 解析器的 `unrestricted | visibleDeptIds | selfOnly(userId)`，日志模块**不自己解析**；操作日志本人列 `oper_id`，登录日志本人列 `user_id`。查询用 MyBatis-Plus wrapper 的 `in`，不写手写 XML（避开 ADR 0050 的存活过滤坑）。
- **DDL**：`sys_oper_log` / `sys_login_log`，迁移 V110。列宽按存储意图：`oper_param`/`error_msg` 用 `VARCHAR(2000)` 而非 `TEXT`，是**故意让 DB 当第二道闸**——应用截断 2000，库再挡一次，bug 不会静默存进 1MB。索引：操作日志 `(create_time)`、`(oper_id, create_time)`、`(dept_id)`；登录日志 `(create_time)`、`(username, create_time)`、`(user_id)`、`(dept_id)`。

考虑过的替代方案：

- **MVC 拦截器按 URL 自动记**（否决）：动作名靠猜，参数采集更粗，没法声明 businessType。
- **service 显式调 recorder**（否决）：审计散到 N 个 service，漏写无法检测。
- **异步 executor 落库**（否决）：仓库零 async 基础设施；自用后台写入量低；同步换来「响应成功=日志在库」且没有提交时序歧义。
- **只写应用日志 / 只落库**（否决）：前者满足不了查询面，后者在 DB 故障时没有任何轨迹。
- **锁定期尝试也落库**（否决）：写放大；ADR 0023 已因同一理由否决过失败落库。
- **部门读时 join `sys_user`**（否决）：审计是历史事实，用户调部门后历史行会漂移；登录失败行还可能没有用户可 join。
- **逻辑删除 + 保留期**（否决）：清空变成假清空，读路径多一层存活过滤；自用无期限不需要归档。
- **导出**（否决）：蓝图未列，且要新引 EasyExcel/POI。
- **两类日志合一表**（否决）：靠 type 列分叉字段与生命周期，查询与约束都变糊；一个 module 一份契约、两张表。
- **`title` 读时 join `sys_menu`**（否决）：菜单改名会重写历史行。

后果：

- 新增 Maven 模块与两张表，新增 `spring-boot-starter-aop` 依赖。
- 后台每个写方法多一个注解；守卫测试让漏挂变红，新后台模块落地时必须同时补注解。
- 登录路径代码不动，但 `LoginAudit` 签名改变：`Slf4jLoginAudit`、`RedisLoginAttemptLimiter` 及其测试要跟着改。
- 登录日志落库让每次「真实密码尝试」多一次写；锁定期不写，攻击面已由 Redis 限流先挡。
- 成功登录与停用账号登录此前无审计事件，现在有。
- 日志表不继承 `BaseEntity`，公共字段填充是否按字段名生效要在实现票验证。
- 新页面的页面授予与按钮点（查询/详情/删除/清空/解锁）归 #210 的菜单 seed；本票只钉语义，不钉 perms 字符串。
- 回归锚点：注解覆盖守卫测试、脱敏与截断单测、登录四事件合同测试、数据范围五档用例（#210 之后补）。

Related: ADR 0023（登录失败计数与审计端口）、ADR 0039（日志脱敏接缝）、ADR 0049（审批账本）、ADR 0050（手写 SQL 存活过滤）、ADR 0052/0053（权限三层与数据范围）、ADR 0024（分页上限）、ADR 0031（公共字段）、ADR 0009（列表读）；issue #211、#210。

2026-11-03 增注（#213–#226 实现收口）：实际迁移号是 V112（`sys_oper_log`）与 V114（`sys_login_log`）；V113/V115 是两组日志菜单种子，已按仓库标准从 `db/migration` 移到 `db/seed`，由同版本的 Java 迁移 `V113__AuditOperLogMenu` / `V115__AuditLoginLogMenu` 执行。audit 模块现在有 DB 版登录日志实现（`LoginLogWriter` 经 security 的 `LoginAudit` 端口接入），不再是「端口已留、实现未落」；「锁定期尝试不落库」的边界不变。

2026-11-05 增注（#263）：落库脱敏从「字段级」扩到「字段名 + 方法参数名」——用户导入把初始密码作为独立 `String` 参数传入，字段黑名单管不到裸标量，`OperParamSanitizer.sanitize(args, parameterNames)` 按 `-parameters` 编译出的参数名把 `initialPassword` 打成 `"***"`（`maven.compiler.parameters` 在父 pom 显式钉住，合同测试同时断言明文不落库）。对象字段的黑名单与 `PhoneNumberMasker` 顺序不变。
