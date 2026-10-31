# 慢 SQL 耗时日志收进 Executor 层拦截器

dev profile 只有 `mybatis-plus.configuration.log-impl: Slf4jImpl`（application-dev.yml:11）输出 SQL 文本，没有耗时、没有阈值。#124 的性能基线之所以能发现 #147（连接池饱和），靠的是 k6 压测加 Hikari 指标，而不是应用日志——「某条 SQL 突然变慢」这个信号此前只能在压测里被看到，一次请求慢时日志分不清是 SQL 慢还是等连接。来自《Spring boot 开发能力清单》第 11 项（#151）。

决定：新增 `SlowSqlLoggingInterceptor`（`common/service-util` 的 `observability` 包，与 `TraceIdFilter` 同居），拦 `Executor.update` 与 `query` 两个签名，超过阈值打 WARN：`statement id、elapsedMs、thresholdMs、rows、params（≤256 字符）、sql（≤1024 字符）`。装配走 `SlowSqlLoggingConfiguration` 条件 bean：`guigu.oa.sql.slow-sql.enabled` 默认 false（base application.yml 显式关闭，与 springdoc #140 同款「base 关、dev 开」），dev 打开，`threshold-ms` 默认 500；关闭时 bean 不注册、零开销，生产临时排障用 `GUIGU_OA_SQL_SLOW_SQL_ENABLED=true` 打开。traceId 不手动关联——`TraceIdFilter` 已把 traceId 写进 MDC，logstash 结构化输出对每行日志自动携带；参数摘要里的手机号由 ADR 0039 的输出层打码覆盖。

只拦 4 参 `query` 即可覆盖全部 mapper 调用（`CachingExecutor` 对 6 参的重载是 `this`-调用，绕过代理，不会双重计时），6 参签名一并注册只是兜底外部直接调用。失败路径不打慢日志：异常的耗时由全局异常日志带栈呈现，慢日志只回答「成功执行的语句花了多久」。拦截器只计时与读取 BoundSql，不改变任何 SQL 语义。

## 考虑过的替代方案

- **p6spy**：引新依赖、换 JDBC 驱动层；issue 已排除——MyBatis `Interceptor` 一层就够，且不改变 SQL 行为。
- **拦 `StatementHandler` 层**：更贴近 JDBC、能拿最终 SQL，但拿不到稳定的 mapper 语句 id 与业务参数摘要；Executor 层两者都有，且与 `MybatisPlusConfig` 的分页拦截器同层可审计。
- **单键 `guigu.oa.sql.slow-threshold`（issue 原建议的键名）**：「默认关闭」得靠哨兵值（0=关）表达；`enabled` + `threshold-ms` 双键自文档，排障时能只翻开关不动阈值。

## 后果

- dev/demo 默认输出慢 SQL WARN（阈值 500ms），生产默认零开销；压测时 `GUIGU_OA_SQL_SLOW_SQL_THRESHOLD_MS=0` 可全量输出每条语句。
- 回归锚点：`SlowSqlLoggingInterceptorTest`（阈值 0 与 Long.MAX_VALUE 夹逼超/未超、update 行数、失败不打、参数与 SQL 截断、BoundSql 二次求值失败降级为 `<unavailable>` 且不外抛）、`SlowSqlLoggingWiringTest`（enabled 三态装配与阈值绑定）、`SlowSqlLoggingContractTest`（经真实 `Plugin` 装配：`selectById` 触发 WARN、`updateById` 触发 WARN、慢日志行携带 MDC traceId）。注意单测直接调 `intercept()` 会绕过 `@Signature` 匹配——签名覆盖必须由合同测试钉住（评审曾据此发现 update 签名漏注册）。
- 本地实测（Compose MySQL + 独立 8801 实例，连接 `guigu-oa` 库、Flyway 关校验，本地 jar 无新增迁移不触碰 schema）：阈值 0 全量输出时登录与列表请求返回 200，同一请求的多条慢 SQL 共享同一 traceId；阈值 5 复跑同一流量，只打 5ms/65ms 两条、3ms 级快语句被过滤——「超过可配置阈值才打」在真实流量上成立。
- 已知边界：阈值 0 等价于「每条语句必打」，只用于测试与临时观测；慢而失败的语句不计入慢日志（见上）。
