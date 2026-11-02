package com.atguigu.observability;

import com.atguigu.common.config.mp.SlowSqlLoggingConfiguration;
import com.atguigu.common.observability.SlowSqlLoggingInterceptor;
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
                .withPropertyValues("guigu.oa.sql.slow-sql.enabled=true",
                        "guigu.oa.sql.slow-sql.threshold-ms=123")
                .run(context -> {
                    assertThat(context).hasSingleBean(Interceptor.class);
                    assertThat(context.getBean(SlowSqlLoggingInterceptor.class).getThresholdMs())
                            .isEqualTo(123L);
                });
    }

    @Test
    void missingPropertyDoesNotRegister() {
        new ApplicationContextRunner()
                .withUserConfiguration(SlowSqlLoggingConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(Interceptor.class));
    }

    @Test
    void explicitFalseDoesNotRegister() {
        new ApplicationContextRunner()
                .withUserConfiguration(SlowSqlLoggingConfiguration.class)
                .withPropertyValues("guigu.oa.sql.slow-sql.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Interceptor.class));
    }
}
