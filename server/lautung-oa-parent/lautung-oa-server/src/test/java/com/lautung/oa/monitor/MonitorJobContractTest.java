package com.lautung.oa.monitor;

import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.model.monitor.SysJobLog;
import com.lautung.oa.monitor.job.MonitorJob;
import com.lautung.oa.monitor.mapper.SysJobLogMapper;
import com.lautung.oa.monitor.service.MonitorJobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 定时任务的 HTTP 合同（#238，ADR 0060）：注册幂等、启停/改 cron 生效、并发禁止跳过、
 * 手动执行记录、失败截断、保留条数 20/500、cron 校验 201、页面授予与按钮点、
 * V120/V121 迁移形状。
 *
 * <p>任务 bean 是测试内注册的三个嵌套配置 bean；执行记录以 {@code sys_job_log} 行为缝观察。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_job_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LoginAuthContractTest.InMemoryRedisConfig.class, MonitorJobContractTest.TestMonitorJobs.class})
class MonitorJobContractTest {

    /** 每轮执行只数数：给自动调度当探针。默认 cron 凌晨三点，测试里不会被误触发。 */
    static class TickerJob implements MonitorJob {
        final AtomicInteger runs = new AtomicInteger();

        @Override
        public String defaultCron() {
            return "0 0 3 * * ?";
        }

        @Override
        public void execute() {
            runs.incrementAndGet();
        }
    }

    /** 一跑就抛长消息：给失败截断当探针。 */
    static class BrokenJob implements MonitorJob {
        @Override
        public String defaultCron() {
            return "0 0 4 * * ?";
        }

        @Override
        public void execute() {
            throw new IllegalStateException("contract boom " + "x".repeat(1500));
        }
    }

    /** execute 阻塞在闭锁上：给并发禁止（忙跳过）当探针。 */
    static class GuardJob implements MonitorJob {
        volatile CountDownLatch latch = new CountDownLatch(1);
        volatile boolean started;
        final AtomicInteger attempts = new AtomicInteger();

        @Override
        public String defaultCron() {
            return "0 0 5 * * ?";
        }

        @Override
        public void execute() {
            attempts.incrementAndGet();
            started = true;
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestMonitorJobs {

        @Bean
        TickerJob contractTickerJob() {
            return new TickerJob();
        }

        @Bean
        BrokenJob contractBrokenJob() {
            return new BrokenJob();
        }

        @Bean
        GuardJob contractGuardJob() {
            return new GuardJob();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private TickerJob tickerJob;

    @Autowired
    private GuardJob guardJob;

    @Autowired
    private MonitorJobScheduler jobScheduler;

    @Autowired
    private SysJobLogMapper jobLogMapper;

    @org.junit.jupiter.api.BeforeEach
    void resetJobFixtures() {
        // 行在用例间持久：复位到「刚登记完」的基线（enabled=0、默认 cron、并发禁止），
        // 执行记录清空——每个用例的观察只来自自己触发的执行。
        guardJob.latch.countDown();
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        try {
            List<Long> enabledIds = jdbcTemplate.queryForList("SELECT id FROM sys_job WHERE enabled = 1", Long.class);
            for (Long id : enabledIds) {
                mockMvc.perform(put("/admin/monitor/jobs/" + id).header("token", token)
                                .contentType(APPLICATION_JSON).content("{\"enabled\": 0}"))
                        .andExpect(jsonPath("$.code").value(200));
            }
        } catch (Exception e) {
            throw new IllegalStateException("复位定时任务基线失败", e);
        }
        jdbcTemplate.update("UPDATE sys_job SET enabled = 0, concurrency = 0, remark = NULL WHERE is_deleted = 0");
        jdbcTemplate.update("UPDATE sys_job SET cron = '0 0 3 * * ?' WHERE bean_name = 'contractTickerJob'");
        jdbcTemplate.update("UPDATE sys_job SET cron = '0 0 4 * * ?' WHERE bean_name = 'contractBrokenJob'");
        jdbcTemplate.update("UPDATE sys_job SET cron = '0 0 5 * * ?' WHERE bean_name = 'contractGuardJob'");
        jdbcTemplate.update("DELETE FROM sys_job_log");
        guardJob.latch = new CountDownLatch(1);
        guardJob.started = false;
    }

    @Test
    void v120CreatesTheJobAndJobLogTablesWithTheirIndexes() {
        // sys_job：票面列形状 + 公共字段 + 逻辑删。
        for (String column : new String[]{
                "id", "name", "bean_name", "cron", "enabled", "concurrency",
                "remark", "create_time", "update_time", "is_deleted"}) {
            assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE UPPER(table_name) = 'SYS_JOB' AND UPPER(column_name) = '" + column.toUpperCase() + "'"),
                    "sys_job 缺列 " + column);
        }
        // sys_job_log：执行记录列，无公共字段（物理表，保留策略负责清）。
        for (String column : new String[]{
                "id", "job_id", "job_name", "trigger_type", "status", "message",
                "started_at", "finished_at", "duration_ms"}) {
            assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE UPPER(table_name) = 'SYS_JOB_LOG' AND UPPER(column_name) = '" + column.toUpperCase() + "'"),
                    "sys_job_log 缺列 " + column);
        }
        assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE UPPER(table_name) = 'SYS_JOB_LOG' AND UPPER(column_name) = 'IS_DELETED'"));
        // 索引：bean_name 唯一、日志 (job_id, started_at) 与 (started_at)。
        assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                + "WHERE UPPER(table_name) = 'SYS_JOB' AND UPPER(index_name) = 'UK_SYS_JOB_BEAN_NAME'"));
        assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                + "WHERE UPPER(table_name) = 'SYS_JOB_LOG' AND UPPER(index_name) = 'IDX_SYS_JOB_LOG_JOB_STARTED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                + "WHERE UPPER(table_name) = 'SYS_JOB_LOG' AND UPPER(index_name) = 'IDX_SYS_JOB_LOG_STARTED'"));
    }

    @Test
    void v120EnforcesUniqueBeanNames() {
        int before = count("SELECT COUNT(*) FROM sys_job");
        jdbcTemplate.update("INSERT INTO sys_job (name, bean_name, cron, enabled, concurrency, is_deleted) "
                + "VALUES ('唯一键探针', 'v120ProbeJob', '0 0 3 * * ?', 0, 0, 0)");
        try {
            jdbcTemplate.update("INSERT INTO sys_job (name, bean_name, cron, enabled, concurrency, is_deleted) "
                    + "VALUES ('唯一键探针二', 'v120ProbeJob', '0 0 4 * * ?', 0, 0, 0)");
            throw new AssertionError("bean_name 唯一键未生效");
        } catch (org.springframework.dao.DuplicateKeyException expected) {
            // 唯一键守住：同 bean_name 第二行拒绝。
        } finally {
            jdbcTemplate.update("DELETE FROM sys_job WHERE bean_name = 'v120ProbeJob'");
        }
        assertEquals(before, count("SELECT COUNT(*) FROM sys_job"));
    }

    @Test
    void v121SeedsTheJobPageAndItsTwoButtonPointsUnderMonitor() {
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = '/monitor/job' AND c.type = 1 "
                + "AND c.component = 'monitor/job/index' AND p.path = '/monitor' AND p.type = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu b JOIN sys_menu p ON p.id = b.parent_id "
                + "WHERE b.type = 2 AND b.is_deleted = 0 AND p.path = '/monitor/job' "
                + "AND b.perms = 'monitor:job:edit'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu b JOIN sys_menu p ON p.id = b.parent_id "
                + "WHERE b.type = 2 AND b.is_deleted = 0 AND p.path = '/monitor/job' "
                + "AND b.perms = 'monitor:job:run'"));
        assertEquals(3, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id "
                + "JOIN sys_menu m ON m.id = rm.menu_id "
                + "WHERE r.role_code = 'super_admin' AND rm.is_deleted = 0 "
                + "AND (m.path = '/monitor/job' "
                + "OR m.perms IN ('monitor:job:edit', 'monitor:job:run'))"));
        assertTrue(count("SELECT COUNT(*) FROM sys_menu WHERE path = '/monitor/job'") >= 1,
                "页面行只种一条且在 /monitor 目录下");
    }

    @Test
    void startupRegistersEachBeanAsADisabledRowWithItsDefaultCron() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        mockMvc.perform(get("/admin/monitor/jobs").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.beanName == 'contractTickerJob')].name", hasItem("contractTickerJob")))
                .andExpect(jsonPath("$.data[?(@.beanName == 'contractTickerJob')].cron", hasItem("0 0 3 * * ?")))
                .andExpect(jsonPath("$.data[?(@.beanName == 'contractTickerJob')].enabled", hasItem(0)))
                .andExpect(jsonPath("$.data[?(@.beanName == 'contractTickerJob')].concurrency", hasItem(0)))
                .andExpect(jsonPath("$.data[?(@.beanName == 'contractTickerJob')].available", hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.beanName == 'contractBrokenJob')].available", hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.beanName == 'contractGuardJob')].available", hasItem(true)))
                // 还没跑过：最近一次执行摘要是空（null 出场，仓库默认序列化形状）。
                .andExpect(jsonPath("$.data[?(@.beanName == 'contractTickerJob')].lastStatus", hasItem(nullValue())));
    }

    @Test
    void reRegistrationIsIdempotentAndKeepsTheUserCron() {
        long tickerId = jobId("contractTickerJob");
        jdbcTemplate.update("UPDATE sys_job SET cron = '0 */7 * * * ?', remark = '人工改过' WHERE id = ?", tickerId);

        // 模拟重启：重新扫描登记，缺行插入、有行不覆盖。
        jobScheduler.registerBeanRows();

        assertEquals(1, count("SELECT COUNT(*) FROM sys_job WHERE bean_name = 'contractTickerJob' AND is_deleted = 0"));
        assertEquals("0 */7 * * * ?",
                jdbcTemplate.queryForObject("SELECT cron FROM sys_job WHERE id = ?", String.class, tickerId));
        assertEquals("人工改过",
                jdbcTemplate.queryForObject("SELECT remark FROM sys_job WHERE id = ?", String.class, tickerId));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_job WHERE bean_name = 'contractBrokenJob' AND enabled = 1"));
    }

    @Test
    void editChangesCronEnabledConcurrencyAndRemarkAndRejectsBadShapes() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        long tickerId = jobId("contractTickerJob");

        mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"cron": "0 0/30 * * * ?", "enabled": 0, "concurrency": 1,
                                 "remark": "合同改过"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("0 0/30 * * * ?",
                jdbcTemplate.queryForObject("SELECT cron FROM sys_job WHERE id = ?", String.class, tickerId));
        assertEquals(1,
                jdbcTemplate.queryForObject("SELECT concurrency FROM sys_job WHERE id = ?", Integer.class, tickerId));
        assertEquals("合同改过",
                jdbcTemplate.queryForObject("SELECT remark FROM sys_job WHERE id = ?", String.class, tickerId));

        // 非法 cron：201 且带 message，行不落改动。
        mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"cron\": \"not-a-cron\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("cron 表达式不合法"));
        assertEquals("0 0/30 * * * ?",
                jdbcTemplate.queryForObject("SELECT cron FROM sys_job WHERE id = ?", String.class, tickerId));

        // enabled / concurrency 出了 0/1：jsr303 形状拒绝。
        mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("enabled 只允许 0 或 1"));
        mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"concurrency\": -1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("concurrency 只允许 0 或 1"));
        mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"remark\": \"" + "长".repeat(256) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("备注不能超过 255 个字符"));

        // 未知任务行：命名拒绝。
        mockMvc.perform(put("/admin/monitor/jobs/99999").header("token", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("任务不存在"));

        // 空体不算改动：字段都不覆盖。
        mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", token)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("合同改过",
                jdbcTemplate.queryForObject("SELECT remark FROM sys_job WHERE id = ?", String.class, tickerId));
    }

    @Test
    void enablingSchedulesFiringEditingCronReCadencesAndDisablingCancels() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        long tickerId = jobId("contractTickerJob");
        long tickerRunsBefore = tickerJob.runs.get();

        // 启用 + 每秒 cron：自动触发落执行记录（trigger_type=1）。
        mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"cron\": \"* * * * * ?\", \"enabled\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertTrue(awaitTrue(() -> tickerJob.runs.get() >= tickerRunsBefore + 2, 10_000),
                "启用后 cron 应开始自动触发");
        assertTrue(awaitTrue(() -> count("SELECT COUNT(*) FROM sys_job_log WHERE job_id = " + tickerId
                + " AND trigger_type = 1 AND status = 1") >= 2, 5_000),
                "自动触发的成功记录应写入 sys_job_log");

        // 启用中改 cron：cancel + reschedule，换成永不触发的 cron 后不再增长。
        mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"cron\": \"0 0 6 1 1 ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        int runsAfterRecadence = tickerJob.runs.get();
        Thread.sleep(2_300);
        assertTrue(tickerJob.runs.get() - runsAfterRecadence <= 1,
                "改 cron 后最多允许一次在途触发，之后应停：before=" + runsAfterRecadence
                        + " after=" + tickerJob.runs.get());

        // 停用：未来被摘除。
        mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(false, jobScheduler.isScheduled(tickerId), "停用后不应再挂着 cron 未来");
    }

    @Test
    void manualRunUsesTheSharedPathAndWritesARecordImmediately() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        long tickerId = jobId("contractTickerJob");
        long runsBefore = tickerJob.runs.get();

        // 异步触发：POST 立即返回；bean 真被调度执行（enabled=0 也可手动执行）。
        mockMvc.perform(post("/admin/monitor/jobs/" + tickerId + "/run").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertTrue(awaitTrue(() -> tickerJob.runs.get() == runsBefore + 1, 5_000),
                "手动执行应驱动 bean 真跑一轮");
        assertTrue(awaitTrue(() -> count("SELECT COUNT(*) FROM sys_job_log WHERE job_id = " + tickerId
                + " AND trigger_type = 2 AND status = 1") >= 1, 5_000),
                "手动执行应记一条成功记录");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT trigger_type, status, started_at, finished_at, duration_ms, message "
                        + "FROM sys_job_log WHERE job_id = ? AND trigger_type = 2", tickerId);
        assertEquals(1, ((Number) row.get("status")).intValue());
        assertNotNull(row.get("started_at"));
        assertNotNull(row.get("finished_at"));
        assertNotNull(row.get("duration_ms"));
        assertNull(row.get("message"));
    }

    @Test
    void failingRunIsCapturedWithTruncatedMessageAndNeverPropagates() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        long brokenId = jobId("contractBrokenJob");

        mockMvc.perform(post("/admin/monitor/jobs/" + brokenId + "/run").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertTrue(awaitTrue(() -> count("SELECT COUNT(*) FROM sys_job_log WHERE job_id = " + brokenId
                + " AND status = 2") >= 1, 5_000), "失败应记「失败」而不是外抛");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT message, LENGTH(message) AS len FROM sys_job_log WHERE job_id = ? AND status = 2", brokenId);
        assertEquals(SysJobLog.MESSAGE_MAX_LENGTH, ((Number) row.get("len")).intValue(),
                "失败信息应截断到 1000 字符");
        assertTrue(((String) row.get("message")).startsWith("java.lang.IllegalStateException: contract boom"),
                "失败信息应保留异常类型与前缀消息");
    }

    @Test
    void concurrencyForbiddenSkipsTheSecondRunWhileTheFirstIsBusy() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        long guardId = jobId("contractGuardJob");

        // 第一轮：占住执行（bean 阻塞在闭锁上）。
        mockMvc.perform(post("/admin/monitor/jobs/" + guardId + "/run").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertTrue(awaitTrue(() -> guardJob.started, 5_000), "第一轮应已进入 execute");

        // 第二轮：并发禁止 + 上一轮未完 → 记「跳过」，不执行 bean。
        mockMvc.perform(post("/admin/monitor/jobs/" + guardId + "/run").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertTrue(awaitTrue(() -> count("SELECT COUNT(*) FROM sys_job_log WHERE job_id = " + guardId
                + " AND status = 3 AND trigger_type = 2") >= 1, 5_000), "忙时应记「跳过」");
        assertEquals(1, guardJob.attempts.get(), "跳过轮不应驱动 bean");

        // 放行第一轮：它正常记「成功」。
        guardJob.latch.countDown();
        assertTrue(awaitTrue(() -> count("SELECT COUNT(*) FROM sys_job_log WHERE job_id = " + guardId
                + " AND status = 1 AND trigger_type = 2") >= 1, 5_000), "第一轮放行后应记「成功」");
    }

    @Test
    void retentionKeepsTwentyPerJobAndFiveHundredGlobally() {
        long tickerId = jobId("contractTickerJob");
        long guardId = jobId("contractGuardJob");

        // 每任务边界：26 条（比保留多 6 条）→ 只剩最近 20 条，最老的 6 条被清。
        for (int i = 1; i <= 26; i++) {
            insertLog(tickerId, i);
        }
        int trimmedPerJob = jobLogMapper.trimPerJob(tickerId, SysJobLog.RETENTION_PER_JOB);
        assertEquals(6, trimmedPerJob);
        assertEquals(SysJobLog.RETENTION_PER_JOB,
                count("SELECT COUNT(*) FROM sys_job_log WHERE job_id = " + tickerId));
        assertEquals(7, minMarker(tickerId), "应保留写入序 7..26，清掉 1..6");
        assertEquals(26, maxMarker(tickerId));

        // 全局边界：再加 505 条（另一任务），全表 525 → 全局只留最近 500 条。
        for (int i = 1; i <= 505; i++) {
            insertLog(guardId, 1_000_000 + i);
        }
        assertEquals(SysJobLog.RETENTION_PER_JOB + 505, count("SELECT COUNT(*) FROM sys_job_log"));
        int trimmedGlobal = jobLogMapper.trimGlobal(SysJobLog.RETENTION_GLOBAL);
        assertEquals(25, trimmedGlobal);
        assertEquals(SysJobLog.RETENTION_GLOBAL, count("SELECT COUNT(*) FROM sys_job_log"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_job_log WHERE job_id = " + tickerId),
                "ticker 的旧行应被全局保留策略清掉");
        assertEquals(1_000_006, minMarker(guardId), "guard 应保留写入序 1000006..1000505");
        assertEquals(1_000_505, maxMarker(guardId));
    }

    @Test
    void logsEndpointReturnsRecentRecordsAndValidatesTheLimit() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        long tickerId = jobId("contractTickerJob");

        // 没跑过：空列表。
        mockMvc.perform(get("/admin/monitor/jobs/" + tickerId + "/logs").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));

        // 手动执行两轮后：新在前，limit 收窄。
        mockMvc.perform(post("/admin/monitor/jobs/" + tickerId + "/run").header("token", token))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/monitor/jobs/" + tickerId + "/run").header("token", token))
                .andExpect(jsonPath("$.code").value(200));
        assertTrue(awaitTrue(() -> count("SELECT COUNT(*) FROM sys_job_log WHERE job_id = " + tickerId) >= 2,
                5_000), "两轮手动执行应各记一条");
        mockMvc.perform(get("/admin/monitor/jobs/" + tickerId + "/logs").param("limit", "1")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].jobId").value((int) tickerId))
                .andExpect(jsonPath("$.data[0].jobName").value("contractTickerJob"))
                .andExpect(jsonPath("$.data[0].triggerType").value(2))
                .andExpect(jsonPath("$.data[0].status").value(1))
                .andExpect(jsonPath("$.data[0].durationMs").exists());

        // limit 边界与未知任务行。
        mockMvc.perform(get("/admin/monitor/jobs/" + tickerId + "/logs").param("limit", "0")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("limit 不合法"));
        mockMvc.perform(get("/admin/monitor/jobs/" + tickerId + "/logs").param("limit", "101")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("limit 不合法"));
        mockMvc.perform(get("/admin/monitor/jobs/99999/logs").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("任务不存在"));
    }

    @Test
    void rowsWhoseBeanIsGoneShowAsUnavailableAndStayUnscheduled() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        jdbcTemplate.update("INSERT INTO sys_job (name, bean_name, cron, enabled, concurrency, is_deleted) "
                + "VALUES ('幽灵任务', 'ghostJob', '* * * * * ?', 1, 0, 0)");
        long ghostId = jobId("ghostJob");
        try {
            mockMvc.perform(get("/admin/monitor/jobs").header("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[?(@.beanName == 'ghostJob')].available", hasItem(false)));
            assertEquals(false, jobScheduler.isScheduled(ghostId), "bean 缺失的行不应被调度");
            // 手动执行也拒绝。
            mockMvc.perform(post("/admin/monitor/jobs/" + ghostId + "/run").header("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.message").value("任务不可用：bean 已不存在"));
            Thread.sleep(2_000);
            assertEquals(0, count("SELECT COUNT(*) FROM sys_job_log WHERE job_id = " + ghostId),
                    "bean 缺失的行不应产生任何自动执行记录");
        } finally {
            jdbcTemplate.update("DELETE FROM sys_job WHERE id = ?", ghostId);
        }
    }

    @Test
    void theJobEndpointsRequireTheirPageAndTheTwoButtonPoints() throws Exception {
        long readOnlyRoleId = 804L;
        long tickerId = jobId("contractTickerJob");
        try {
            String roleless = jwtHelper.createTokenPair(2L, "user", 0L).token();
            mockMvc.perform(get("/admin/monitor/jobs").header("token", roleless))
                    .andExpect(jsonPath("$.code").value(205));
            mockMvc.perform(get("/admin/monitor/jobs/" + tickerId + "/logs").header("token", roleless))
                    .andExpect(jsonPath("$.code").value(205));
            mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", roleless)
                            .contentType(APPLICATION_JSON).content("{\"enabled\": 1}"))
                    .andExpect(jsonPath("$.code").value(205));
            mockMvc.perform(post("/admin/monitor/jobs/" + tickerId + "/run").header("token", roleless))
                    .andExpect(jsonPath("$.code").value(205));

            // 只授页面：两个读放行，两个写仍 205。
            grantJobPage(readOnlyRoleId);
            String pageOnly = jwtHelper.createTokenPair(2L, "user", 0L).token();
            mockMvc.perform(get("/admin/monitor/jobs").header("token", pageOnly))
                    .andExpect(jsonPath("$.code").value(200));
            mockMvc.perform(get("/admin/monitor/jobs/" + tickerId + "/logs").header("token", pageOnly))
                    .andExpect(jsonPath("$.code").value(200));
            mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", pageOnly)
                            .contentType(APPLICATION_JSON).content("{\"enabled\": 1}"))
                    .andExpect(jsonPath("$.code").value(205));
            mockMvc.perform(post("/admin/monitor/jobs/" + tickerId + "/run").header("token", pageOnly))
                    .andExpect(jsonPath("$.code").value(205));

            // 补 edit 按钮：启停放行（顺带停回 0）。
            grantJobButton(readOnlyRoleId, "monitor:job:edit");
            String editor = jwtHelper.createTokenPair(2L, "user", 0L).token();
            mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", editor)
                            .contentType(APPLICATION_JSON).content("{\"enabled\": 1}"))
                    .andExpect(jsonPath("$.code").value(200));
            mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", editor)
                            .contentType(APPLICATION_JSON).content("{\"enabled\": 0}"))
                    .andExpect(jsonPath("$.code").value(200));
            mockMvc.perform(post("/admin/monitor/jobs/" + tickerId + "/run").header("token", editor))
                    .andExpect(jsonPath("$.code").value(205));

            // 补 run 按钮：手动执行放行。
            grantJobButton(readOnlyRoleId, "monitor:job:run");
            String runner = jwtHelper.createTokenPair(2L, "user", 0L).token();
            mockMvc.perform(post("/admin/monitor/jobs/" + tickerId + "/run").header("token", runner))
                    .andExpect(jsonPath("$.code").value(200));
        } finally {
            jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", readOnlyRoleId);
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id = ?", readOnlyRoleId);
            jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", readOnlyRoleId);
        }
    }

    @Test
    void theWriteActionsEachWriteAnOperLogRow() throws Exception {
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        long tickerId = jobId("contractTickerJob");

        mockMvc.perform(put("/admin/monitor/jobs/" + tickerId).header("token", token)
                        .contentType(APPLICATION_JSON).content("{\"remark\": \"留痕\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/monitor/jobs/" + tickerId + "/run").header("token", token))
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(1, count("SELECT COUNT(*) FROM sys_oper_log WHERE title = '定时任务·修改'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_oper_log WHERE title = '定时任务·手动执行'"));
    }

    private void grantJobPage(long roleId) {
        if (count("SELECT COUNT(*) FROM sys_role WHERE id = " + roleId) == 0) {
            jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, is_deleted) "
                    + "VALUES (?, '定时任务只读', 'job_readonly', 0)", roleId);
            jdbcTemplate.update("INSERT INTO sys_user_role (role_id, user_id, is_deleted) VALUES (?, 2, 0)",
                    roleId);
        }
        Integer menuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/monitor/job' AND type = 1 AND is_deleted = 0",
                Integer.class);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                roleId, menuId);
    }

    private void grantJobButton(long roleId, String perms) {
        Integer buttonId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND type = 2 AND is_deleted = 0", Integer.class, perms);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                roleId, buttonId);
    }

    private void insertLog(long jobId, int marker) {
        jdbcTemplate.update("INSERT INTO sys_job_log (job_id, job_name, trigger_type, status, started_at, finished_at, duration_ms) "
                + "VALUES (?, '探针', 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)", jobId, (long) marker);
    }

    /** 探针写入序的最小/最大标记（duration_ms 承载），与自增 id 的分配顺序解耦。 */
    private int minMarker(long jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT MIN(duration_ms) FROM sys_job_log WHERE job_id = " + jobId, Integer.class);
    }

    private int maxMarker(long jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(duration_ms) FROM sys_job_log WHERE job_id = " + jobId, Integer.class);
    }

    private boolean awaitTrue(java.util.function.BooleanSupplier condition, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }

    private long jobId(String beanName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_job WHERE bean_name = ? AND is_deleted = 0", Long.class, beanName);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
