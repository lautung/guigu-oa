package com.atguigu.common.observability;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Executor 层慢 SQL 观测（#151）：只计时与打日志，不改变任何 SQL 语义。
 * 请求侧 traceId 由 {@link TraceIdFilter} 写入 MDC，结构化输出自动携带，无需手动关联。
 * 失败路径不打慢日志：异常耗时由全局异常日志携带栈呈现。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, org.apache.ibatis.session.RowBounds.class,
                        org.apache.ibatis.session.ResultHandler.class})
})
public class SlowSqlLoggingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SlowSqlLoggingInterceptor.class);
    /** 参数摘要与 SQL 文本各设上限，慢日志行不因大对象参数失控。 */
    static final int MAX_PARAMS_LENGTH = 256;
    static final int MAX_SQL_LENGTH = 1024;

    private final long thresholdMs;

    public SlowSqlLoggingInterceptor(long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    public long getThresholdMs() {
        return thresholdMs;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startedAt = System.nanoTime();
        Object result = invocation.proceed();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        if (elapsedMs >= thresholdMs) {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs()[1];
            Object rows = result instanceof List<?> list ? list.size()
                    : result instanceof Integer affected ? affected : -1;
            // getBoundSql 会对动态 SQL 做第二次求值；求值失败不得把已成功的语句变成失败请求。
            String sql;
            try {
                sql = summarize(statement.getBoundSql(parameter).getSql(), MAX_SQL_LENGTH);
            } catch (RuntimeException e) {
                sql = "<unavailable>";
            }
            log.warn("slow sql statement={} elapsedMs={} thresholdMs={} rows={} params={} sql={}",
                    statement.getId(), elapsedMs, thresholdMs, rows,
                    summarize(String.valueOf(parameter), MAX_PARAMS_LENGTH), sql);
        }
        return result;
    }

    private String summarize(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(truncated)";
    }
}
