package com.atguigu.observability;

import com.atguigu.common.observability.SlowSqlLoggingInterceptor;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #151：慢 SQL 日志的阈值语义。阈值语义用 0 与 Long.MAX_VALUE 确定性夹逼，不依赖真实睡眠。 */
@ExtendWith(OutputCaptureExtension.class)
class SlowSqlLoggingInterceptorTest {

    private static final String STATEMENT_ID = "com.atguigu.auth.mapper.SysUserMapper.selectById";
    private static final String UPDATE_STATEMENT_ID = "com.atguigu.auth.mapper.SysUserMapper.updateStatus";
    private static final String SQL_TEXT = "SELECT id, username FROM sys_user WHERE id = ?";

    @Test
    void queryOverThresholdWarnsWithStatementIdRowsParamsAndSql(CapturedOutput output) throws Throwable {
        SlowSqlLoggingInterceptor interceptor = new SlowSqlLoggingInterceptor(0L);
        Executor executor = executorQueryReturning(List.of("row-1", "row-2"));

        Map<String, Object> params = new HashMap<>();
        params.put("id", 1L);
        Invocation invocation = queryInvocation(executor, params);

        interceptor.intercept(invocation);

        String logs = output.toString();
        assertTrue(logs.contains("slow sql statement=" + STATEMENT_ID), logs);
        assertTrue(logs.contains("thresholdMs=0"), logs);
        assertTrue(logs.contains("rows=2"), logs);
        assertTrue(logs.contains("params={id=1}"), logs);
        assertTrue(logs.contains("sql=SELECT id, username FROM sys_user WHERE id = ?"), logs);
    }

    @Test
    void queryUnderThresholdDoesNotLog(CapturedOutput output) throws Throwable {
        SlowSqlLoggingInterceptor interceptor = new SlowSqlLoggingInterceptor(Long.MAX_VALUE);
        Executor executor = executorQueryReturning(List.of("row-1"));

        interceptor.intercept(queryInvocation(executor, Map.of("id", 1L)));

        assertTrue(output.toString().isEmpty(), "未超阈值不应产生任何输出: " + output);
    }

    @Test
    void updateOverThresholdWarnsWithAffectedRows(CapturedOutput output) throws Throwable {
        SlowSqlLoggingInterceptor interceptor = new SlowSqlLoggingInterceptor(0L);
        Executor executor = Mockito.mock(Executor.class);
        Mockito.when(executor.update(Mockito.any(MappedStatement.class), Mockito.any())).thenReturn(3);

        Invocation invocation = updateInvocation(executor, Map.of("id", 1L));

        interceptor.intercept(invocation);

        String logs = output.toString();
        assertTrue(logs.contains("slow sql statement=" + UPDATE_STATEMENT_ID), logs);
        assertTrue(logs.contains("rows=3"), logs);
        assertTrue(logs.contains("sql=UPDATE sys_user SET status = 1 WHERE id = ?"), logs);
    }

    @Test
    void failedStatementPropagatesWithoutSlowLog(CapturedOutput output) throws Throwable {
        SlowSqlLoggingInterceptor interceptor = new SlowSqlLoggingInterceptor(0L);
        Executor executor = Mockito.mock(Executor.class);
        Mockito.when(executor.update(Mockito.any(MappedStatement.class), Mockito.any()))
                .thenThrow(new RuntimeException("constraint violated"));

        Invocation invocation = updateInvocation(executor, Map.of("id", 1L));

        // Invocation.proceed 走反射，真实链路由 Plugin 解包；边界上断言包装异常与其 cause。
        Throwable thrown = org.junit.jupiter.api.Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> interceptor.intercept(invocation));
        org.junit.jupiter.api.Assertions.assertEquals("constraint violated", thrown.getCause().getMessage());
        assertTrue(output.toString().isEmpty(), "失败路径不打慢 SQL 日志: " + output);
    }

    /** getBoundSql 二次求值失败不得把已成功的语句变成失败请求：日志降级但不缺行、异常不外抛。 */
    @Test
    void boundSqlEvaluationFailureDoesNotFailTheSucceededStatement(CapturedOutput output) throws Throwable {
        SlowSqlLoggingInterceptor interceptor = new SlowSqlLoggingInterceptor(0L);
        Executor executor = executorQueryReturning(List.of("row-1"));

        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, STATEMENT_ID,
                new org.apache.ibatis.mapping.SqlSource() {
                    @Override
                    public org.apache.ibatis.mapping.BoundSql getBoundSql(Object parameterObject) {
                        throw new IllegalStateException("ognl blew up");
                    }
                }, SqlCommandType.SELECT).build();
        Method query = Executor.class.getMethod("query",
                MappedStatement.class, Object.class, RowBounds.class, org.apache.ibatis.session.ResultHandler.class);
        Invocation invocation = new Invocation(executor, query,
                new Object[]{statement, Map.of("id", 1L), RowBounds.DEFAULT, null});

        Object result = interceptor.intercept(invocation);

        org.junit.jupiter.api.Assertions.assertEquals(List.of("row-1"), result, "成功结果必须原样返回");
        String logs = output.toString();
        assertTrue(logs.contains("slow sql statement=" + STATEMENT_ID), logs);
        assertTrue(logs.contains("sql=<unavailable>"), logs);
    }

    private Executor executorQueryReturning(List<?> rows) throws java.sql.SQLException {
        Executor executor = Mockito.mock(Executor.class);
        Mockito.doReturn(rows).when(executor).query(Mockito.any(MappedStatement.class), Mockito.any(),
                Mockito.any(RowBounds.class), Mockito.any());
        return executor;
    }

    private Invocation updateInvocation(Executor executor, Object parameter) throws NoSuchMethodException {
        Method update = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, UPDATE_STATEMENT_ID,
                new StaticSqlSource(configuration, "UPDATE sys_user SET status = 1 WHERE id = ?"),
                SqlCommandType.UPDATE).build();
        return new Invocation(executor, update, new Object[]{statement, parameter});
    }

    @Test
    void longParamsAndSqlAreTruncated(CapturedOutput output) throws Throwable {
        SlowSqlLoggingInterceptor interceptor = new SlowSqlLoggingInterceptor(0L);
        Executor executor = executorQueryReturning(List.of("row-1"));

        String longParam = "{payload=" + "v".repeat(3000) + "}";
        String longSql = "SELECT " + "c".repeat(2000);
        Invocation invocation = queryInvocation(executor, Map.of("payload", "v".repeat(3000)), longSql);

        interceptor.intercept(invocation);

        String logs = output.toString();
        assertTrue(logs.contains(longParam.substring(0, 256) + "...(truncated)"), logs);
        assertTrue(logs.contains(longSql.substring(0, 1024) + "...(truncated)"), logs);
        assertFalse(logs.contains(longParam), "完整长参数不应整段出现在日志里");
        assertFalse(logs.contains(longSql), "完整长 SQL 不应整段出现在日志里");
    }

    private Invocation queryInvocation(Executor executor, Object parameter, String sqlText) throws NoSuchMethodException {
        Method query = Executor.class.getMethod("query",
                MappedStatement.class, Object.class, RowBounds.class, org.apache.ibatis.session.ResultHandler.class);
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, STATEMENT_ID,
                new StaticSqlSource(configuration, sqlText), SqlCommandType.SELECT).build();
        return new Invocation(executor, query,
                new Object[]{statement, parameter, RowBounds.DEFAULT, null});
    }

    private Invocation queryInvocation(Executor executor, Object parameter) throws NoSuchMethodException {
        return queryInvocation(executor, parameter, SQL_TEXT);
    }
}
