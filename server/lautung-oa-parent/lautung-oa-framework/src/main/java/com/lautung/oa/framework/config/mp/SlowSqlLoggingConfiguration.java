package com.lautung.oa.framework.config.mp;

import com.lautung.oa.framework.observability.SlowSqlCollector;
import com.lautung.oa.framework.observability.SlowSqlLoggingInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * #151：慢 SQL 日志装配。默认关闭（base application.yml 显式 enabled: false），
 * dev/demo 打开；生产临时排障用 LAUTUNG_OA_SQL_SLOW_SQL_ENABLED=true 环境变量打开。
 * MyBatis-Plus starter 会把容器里的 Interceptor bean 装进 SqlSessionFactory。
 * ADR 0060：开关打开时同时注册 {@link SlowSqlCollector} 内存环（数据监控读它），
 * 关闭时两者都不存在，数据监控显示「采集未开启」。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lautung.oa.sql.slow-sql", name = "enabled", havingValue = "true")
public class SlowSqlLoggingConfiguration {

    @Bean
    public SlowSqlCollector slowSqlCollector() {
        return new SlowSqlCollector();
    }

    @Bean
    public Interceptor slowSqlLoggingInterceptor(
            @Value("${lautung.oa.sql.slow-sql.threshold-ms:500}") long thresholdMs,
            SlowSqlCollector slowSqlCollector) {
        return new SlowSqlLoggingInterceptor(thresholdMs, slowSqlCollector);
    }
}
