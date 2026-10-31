package com.atguigu.common.config.mp;

import com.atguigu.common.observability.SlowSqlLoggingInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * #151：慢 SQL 日志装配。默认关闭（base application.yml 显式 enabled: false），
 * dev/demo 打开；生产临时排障用 GUIGU_OA_SQL_SLOW_SQL_ENABLED=true 环境变量打开。
 * MyBatis-Plus starter 会把容器里的 Interceptor bean 装进 SqlSessionFactory。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "guigu.oa.sql.slow-sql", name = "enabled", havingValue = "true")
public class SlowSqlLoggingConfiguration {

    @Bean
    public Interceptor slowSqlLoggingInterceptor(
            @Value("${guigu.oa.sql.slow-sql.threshold-ms:500}") long thresholdMs) {
        return new SlowSqlLoggingInterceptor(thresholdMs);
    }
}
