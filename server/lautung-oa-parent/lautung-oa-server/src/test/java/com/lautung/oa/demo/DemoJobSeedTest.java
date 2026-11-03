package com.lautung.oa.demo;

import com.lautung.oa.monitor.job.MonitorJob;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * demo 演示定时任务的种子合同（#238，ADR 0060）：任务行由启动登记产生（enabled=0），
 * seeder 只做改名与备注的化妆；演示 bean 的默认 cron 必须是合法的 Spring cron，
 * execute 只写一行日志。
 */
class DemoJobSeedTest {

    @Test
    void seederRenamesTheDemoJobRowAndNeverTouchesEnabled() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DemoScenarioSeeder seeder = new DemoScenarioSeeder(
                null, null, null, null, null, jdbc, null, null);

        seeder.ensureDemoJobRow();

        verify(jdbc).update(anyString(),
                eq(DemoScenarioSeeder.DEMO_JOB_NAME),
                eq(DemoScenarioSeeder.DEMO_JOB_REMARK),
                eq(DemoScenarioSeeder.DEMO_JOB_BEAN));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void demoJobBeanDeclaresAValidCronAndExecuteOnlyLogsOneLine() {
        MonitorJob job = new DemoLogMonitorJob();
        assertDoesNotThrow(() -> CronExpression.parse(job.defaultCron()),
                "演示任务的默认 cron 必须能被 Spring 解析");
        assertEquals(DemoScenarioSeeder.DEMO_JOB_BEAN, "demoLogMonitorJob",
                "登记行按 bean 名对上，改名才不会落空");
        assertDoesNotThrow(job::execute);
    }
}
