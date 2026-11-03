# 系统监控收成一个模块，在线会话用 Redis 登记，actuator 不外扩

#233 把蓝图 P1/P2 的监控组（在线用户 / 服务监控 / 缓存监控 / 缓存列表 / 数据监控 / 定时任务）定到可实现形状。侦察到五件与票面假设不一致或票面没写的事实：

- **会话登记不存在，但 `sessionId` 已具备**：一次登录签发的一对 access/refresh 令牌共享 `sessionId`（`JwtHelper`），退出已按它写 `token:revoked:<sessionId>`；缺的只是「登录时登记、可列举、可删除」。`/info` 目前不返回 sessionId，客户端无法标出当前会话。
- **权限缓存键是裸 username**：`IndexController.refreshCachedAuthorities` 直接 `set(username, json)`，`PermissionCacheInvalidator` 也按裸 username 删。缓存列表若按前缀白名单浏览，会先被这一批无前缀键撞破。
- **actuator 只开 health**（`application.yml:42-50`，匿名白名单只含 `/actuator/health`）。服务/缓存监控要动暴露面，是安全面决策。
- **慢 SQL 只进日志**：ADR 0041 的拦截器按 `enabled` 条件装配，dev 开、base 关，没有落库也没有聚合。
- **没有任何任务表**；`@EnableScheduling` 已开，只有附件清理与公告图片清理两处内部 `@Scheduled`。

票面的权限点示例（`monitor:online:list` / `monitor:server:view` / `monitor:cache:view` / `monitor:job:list`）与 ADR 0056 冲突：读不设点、页面授予覆盖读，动作只用 `add/edit/remove/forceLogout/clean/...`。本 ADR 按 0056 收口。

决定：

- **模块归属**：新建 `lautung-oa-monitor`（`com.lautung.oa.monitor`）装五个监控面与定时任务；**会话登记与强退端口留在 security**（登录写登记、强退 = 吊销 + 注销，两件事同一个生活史），monitor 只调用端口。实体进 `lautung-oa-model`，ArchUnit 增加 `Monitor` 层；依赖方向 `monitor → security/model/framework/common`。
- **在线用户 = Redis 会话登记**：一次登录一个 hash，键 `login:session:<sessionId>`，TTL 取 refresh 令牌寿命（7 天，`JwtHelper.REFRESH_TOKEN_LIFETIME_MILLIS`，与吊销键同寿）；字段 `userId / username / name 快照 / loginTime / ip / browser / os / lastActive / tokenVersion`。**一行 = 一次登录**，同账号多端各占一行。不建 DB 表，不建 user→sessions 索引。
- **最后活跃**：核验过滤器里按会话节流写，≥60 秒一次（用 `SET NX EX 60` 的节流键判定，命中了才 `HSET lastActive`），不延长 hash 的 TTL——会话寿命只由 refresh 令牌决定，不因为活跃而续命。
- **当前会话标记**：`/info` 增补 `sessionId`（ADR 0058 的小扩展）；在线用户页据此标出本机会话并禁用该行的强退按钮。不在登录响应里加字段，客户端在需要时读 `/info` 即可。
- **在线列表的失效口径**：读时按用户行现值过滤——用户不存在/已删/停用/`token_version` 与登记值不一致的会话直接不展示，也不删键（键由 TTL 收尾）；退出与强退会主动删键。这样改密与停用不需要额外的联动写，页面也不会显示「假在线」。列举用 `SCAN login:session:*`，在内存中过滤排序后分页，不用 `KEYS`。
- **强退 = 单会话**：GLOSSARY「在线用户」已定「强退只作废那一个会话」，因此只做单会话强退，不做「强退该用户全部会话」按钮——全端作废已有两条现成路径：改密（`token_version` 递增）与停用（核验拒绝）。security 暴露一个 `forceLogout(sessionId)`：吊销该 sessionId + 删登记键，不改用户状态。二次确认显示用户名、登录时间、IP；强退自己那台时下一次请求按 208 处理，客户端沿用现有的清凭证回登录页。
- **服务监控不外扩 actuator**：保持 `health` only、`show-details: never`、匿名白名单不变；自研 `GET /admin/monitor/server`，控制器直读 JMX（`com.sun.management.OperatingSystemMXBean` 的 CPU/负载、`MemoryMXBean`、内存池、`ThreadMXBean`、`FileStore` 磁盘）与 Hikari 的 `HikariPoolMXBean`，返回一份稳定 JSON，走常规页面授予。不引 Micrometer/Prometheus，不代理 actuator 端点。
- **数据监控不落库**：Hikari 实时指标（active/idle/total/waiting/max + 累计获取/超时）与慢 SQL 的**内存环形缓冲最近 200 条**同页展示。缓冲由 `SlowSqlCollector` 持有，现有 `SlowSqlLoggingInterceptor` 打日志时顺带写入；**不存参数**（ADR 0039 的手机号打码在日志输出层，缓冲若带参数等于给 API 留一条绕过打码的路径），只存时间、语句 id、elapsedMs、rows 与截断到 1024 字符的 SQL。采集跟随 `lautung.oa.sql.slow-sql.enabled`，关闭时页面显示「采集未开启」；不做采样、不做保留期、不建表。demo profile 打开慢 SQL，让演示有内容。
- **缓存监控两页**：缓存监控页只取 Redis `INFO` 的 `server / memory / clients / stats / keyspace` 五段并解析成字段；缓存列表页用 `SCAN` + 游标分页浏览，**只接受前缀白名单** `perm: / login:session: / login:attempt: / token:revoked:`，不用 `KEYS`。为此把权限缓存键从裸 username 改为 `perm:cache:<username>`：新增一个 key 构造点，读、写、失效三处共用；写入新键时顺手删旧裸键，存量键随各用户下一次登录自然清掉。
- **缓存清理只允许 `perm:*`**：单键删除与按前缀清空权限缓存两个动作，都挂 `monitor:cache:clean` 按钮点并二次确认；其余前缀**只读**——删掉 `token:revoked:*` 等于把已退出的会话复活。不做通用按键删除，也不做 flushdb。
- **定时任务 = 注解注册 + 表驱动启停**：任务是实现 `MonitorJob` 接口（`defaultCron()` + `execute()`）的 Spring bean，启动时把 bean 名登记进 `sys_job`（缺行插入、有行不覆盖用户改过的 cron）；表里 bean 不存在或 bean 不在表里的都不执行，前者在读侧标「异常」。**任务行由启动登记产生，页面不提供新增与删除**（删掉的行下次启动会被补回），只能改 cron / 启停 / 并发 / 备注并手动执行。调度用 `ThreadPoolTaskScheduler`：启动时装载启用的行，启停或改 cron 时 cancel + reschedule；单机语义，不处理集群。不引入 Quartz，也不接收任意类名或调用串（GLOSSARY「定时任务」已定）。
- **任务表与执行记录**：`sys_job(id, name, bean_name 唯一, cron, enabled, concurrency, remark, 公共字段, is_deleted)` + `sys_job_log(id, job_id, job_name 快照, trigger_type, status, message, started_at, finished_at, duration_ms)`。`trigger_type` 1 自动 / 2 手动；`status` 1 成功 / 2 失败 / 3 跳过。
- **并发两档**：`0 禁止`（默认，前一执行未完则本次直接记一条「跳过」）/ `1 允许`；不做「单机」档——项目单实例部署，该档没有可表达的语义。cron 在写入时用 Spring `CronExpression` 校验，非法按 201 拒绝。
- **失败可见性**：任务页的行上「执行记录」抽屉，不单开菜单页；失败存截断到 1000 字符的错误信息。保留策略在写入时执行：每个任务保留最近 20 条、全局最近 500 条，不做告警、不做导出、不做清空按钮。
- **既有 `@Scheduled` 不动**：附件清理与公告图片清理是与配置绑定的内部维护（fixed-delay，不是 cron），不进 `sys_job`、不出现在任务页。
- **菜单与权限点**（沿用 ADR 0056 命名，读不设点）：

  | 页面 | 路径 | 按钮点 |
  | --- | --- | --- |
  | 在线用户 | `/monitor/online` | `monitor:online:forceLogout` |
  | 服务监控 | `/monitor/server` | —（只读） |
  | 缓存监控 | `/monitor/cache` | —（只读） |
  | 缓存列表 | `/monitor/cacheList` | `monitor:cache:clean` |
  | 数据监控 | `/monitor/data` | —（只读） |
  | 定时任务 | `/monitor/job` | `monitor:job:edit` / `:run` |

  目录 `/monitor`（type 0）与六个页面（type 1）随各自服务端票一次性种下并给 `super_admin` 补授；启停并入 `edit`，任务日志读不设点。监控不套数据范围（ADR 0053 已定），页面授予覆盖全部读。
- **演示数据**：在线用户不种（运行时产物，demo 登录自然产生）；定时任务在 demo profile 种一条 **disabled** 的演示任务，演示时手动执行一次即产生执行记录；服务/缓存/数据页不种，真实 Redis/MySQL 即有内容。

考虑过的替代方案：

- **在线会话落 `sys_online_session` 表**（否决）：要多一套清理作业，登录多一次 DB 写，还要和吊销键维护两套寿命；Redis 已经是会话真相，登记与吊销同库同寿。
- **user→sessions 索引**（否决）：只为「强退该用户全部会话」服务；GLOSSARY 已否掉全端强退，改密与停用已覆盖全端作废，索引只会多一份要清理的集合。
- **开 `/actuator/metrics` 或 `env/threaddump`**（否决）：JSON 形状随版本漂移、暴露面大，还要扩匿名白名单；自研薄读一份稳定形状更可控，改造成本也就是几个 JMX 调用。
- **慢 SQL 落库做历史查询**（否决）：写放大加保留期治理，对一个 P2 自用面板不值；内存环重启即清，正好匹配「看当下」的用途。
- **缓冲里带参数便于排障**（否决）：参数含手机号，日志侧的打码不会覆盖成 API 响应，等于开一条绕过 ADR 0039 的路径；排障去看日志，面板只回答「哪条语句慢」。
- **权限缓存键不加前缀、缓存列表按白名单过滤掉裸键**（否决）：权限缓存会变成不可浏览、不可清理的黑盒，裸 username 键本身也容易和别的键撞名。
- **Quartz 或任意调用串**（否决）：GLOSSARY 已定「不是任意代码调用串」；单实例下 `ThreadPoolTaskScheduler` 足够。
- **并发第三档「单机」**（否决）：单实例部署没有可表达的语义，留着只会让实现分叉。

后果：

- 登录链路多一次 Redis 写，核验链路每会话每分钟最多一次写；Redis 不可用时登录失败，与权限缓存/吊销同故障面。
- `/info` 形状多一个 `sessionId` 字段（ADR 0058 扩展）；读取方按新增字段处理，不改变既有三段。
- 权限缓存键迁移：`IndexController`、`PermissionCacheInvalidator` 与读缓存处统一走 key 构造点；部署后旧裸键在用户下一次登录时被删，未登录用户的旧键自然成为孤儿（无 TTL），由后续登录或人工清理收掉。
- 新模块 `lautung-oa-monitor` 进 Maven 模块表与 ArchUnit 规则；新页面模板仍然是「菜单行 + 按钮点 + 模块迁移补授 `super_admin` + 控制器 gate + 合同测试」。
- `sys_job` / `sys_job_log` 两张新表；任务日志的保留策略写在写入路径，不依赖外部定时器。
- 回归锚点：会话登记 TTL 与最后活跃节流、改密/停用后在线列表不显示旧会话、强退只影响一个会话且自己那台 208、actuator 暴露面仍只有 health（合同测试）、慢 SQL 缓冲无参数字段、权限缓存键迁移后读写一致、cron 校验与并发跳过、任务日志保留条数。

Related: #233（本票）；#206（地图）；ADR 0022（退出按登录凭证作废）、0029（改密作废全部会话）、0039（日志脱敏）、0041（慢 SQL 日志）、0052/0053（按钮点与数据范围）、0056（菜单与权限点命名）、0058（`/info` 形状）、`GLOSSARY.md`「在线用户」「定时任务」「监控」。
