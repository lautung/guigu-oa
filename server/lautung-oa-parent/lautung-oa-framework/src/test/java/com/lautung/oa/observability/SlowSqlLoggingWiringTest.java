package com.lautung.oa.observability;

import com.lautung.oa.framework.config.mp.SlowSqlLoggingConfiguration;
import com.lautung.oa.framework.observability.SlowSqlCollector;
import com.lautung.oa.framework.observability.SlowSqlLoggingInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** #151：slow-sql 开关与阈值的装配事实——默认不注册，enabled=true 才注册且阈值正确绑定。 */
class SlowSqlLoggingWiringTest {

    @Test
    void enabledPropertyRegistersInterceptorWithBoundThreshold() {
        new ApplicationContextRunner()
                .withUserConfiguration(SlowSqlLoggingConfiguration.class)
                .withPropertyValues("lautung.oa.sql.slow-sql.enabled=true",
                        "lautung.oa.sql.slow-sql.threshold-ms=123")
                .run(context -> {
                    assertThat(context).hasSingleBean(Interceptor.class);
                    assertThat(context.getBean(SlowSqlLoggingInterceptor.class).getThresholdMs())
                            .isEqualTo(123L);
                });
    }

    /** ADR 0060：缓冲与拦截器同生同灭——enabled 才有 collector，数据监控据此显示「采集未开启」。 */
    @Test
    void enabledPropertyAlsoRegistersTheCollectorTheInterceptorWritesInto() {
        new ApplicationContextRunner()
                .withUserConfiguration(SlowSqlLoggingConfiguration.class)
                .withPropertyValues("lautung.oa.sql.slow-sql.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SlowSqlCollector.class);
                    assertThat(context).hasSingleBean(SlowSqlLoggingInterceptor.class);
                });
    }

    @Test
    void missingPropertyDoesNotRegister() {
        new ApplicationContextRunner()
                .withUserConfiguration(SlowSqlLoggingConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(Interceptor.class));
    }

    @Test
    void missingPropertyDoesNotRegisterTheCollectorEither() {
        new ApplicationContextRunner()
                .withUserConfiguration(SlowSqlLoggingConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(SlowSqlCollector.class));
    }

    @Test
    void explicitFalseDoesNotRegister() {
        new ApplicationContextRunner()
                .withUserConfiguration(SlowSqlLoggingConfiguration.class)
                .withPropertyValues("lautung.oa.sql.slow-sql.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Interceptor.class));
    }

    @Test
    void explicitFalseDoesNotRegisterTheCollectorEither() {
        new ApplicationContextRunner()
                .withUserConfiguration(SlowSqlLoggingConfiguration.class)
                .withPropertyValues("lautung.oa.sql.slow-sql.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SlowSqlCollector.class));
    }
}
