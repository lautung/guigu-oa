package com.lautung.oa.monitor;

import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.support.ContractFixtures;
import com.lautung.oa.support.InMemoryRedisTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 服务监控与数据监控的 HTTP 合同（ADR 0060）：JMX/Hikari 直读形状、分段降级不 500、
 * 采集关闭态（enabled=false 且 entries 空）、页面授予（无按钮点，205 家族）。
 *
 * <p>test profile 慢 SQL 默认关闭（base application.yml enabled: false），
 * 关闭态合同在这里钉住；开启态在 SlowSqlBufferDataContractTest。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_monitor_server_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class MonitorServerDataContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final long READ_ONLY_ROLE_ID = 802L;

    @BeforeEach
    @AfterEach
    void resetFixtures() {
        ((InMemoryRedisTemplate) redisTemplate).clear();
        ContractFixtures.resetSeededUsers(jdbcTemplate);
        ContractFixtures.deleteAdHocRole(jdbcTemplate, READ_ONLY_ROLE_ID);
    }

    @Test
    void serverSnapshotCarriesCpuMemoryJvmDiskAndHikariSections() throws Exception {
        mockMvc.perform(get("/admin/monitor/server")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cpu.availableProcessors").isNumber())
                .andExpect(jsonPath("$.data.memory.totalBytes").isNumber())
                .andExpect(jsonPath("$.data.jvm.heap.usedBytes").isNumber())
                .andExpect(jsonPath("$.data.jvm.heap.committedBytes").isNumber())
                .andExpect(jsonPath("$.data.jvm.nonHeap.usedBytes").isNumber())
                .andExpect(jsonPath("$.data.jvm.pools").isArray())
                .andExpect(jsonPath("$.data.jvm.threads.threadCount").isNumber())
                .andExpect(jsonPath("$.data.jvm.threads.peakThreadCount").isNumber())
                .andExpect(jsonPath("$.data.jvm.threads.daemonThreadCount").isNumber())
                .andExpect(jsonPath("$.data.jvm.uptimeSeconds").isNumber())
                .andExpect(jsonPath("$.data.disks").isArray())
                // 合同测试的 H2 数据源就是 Hikari：整段必须可读。
                .andExpect(jsonPath("$.data.hikari.maxPoolSize").isNumber())
                .andExpect(jsonPath("$.data.hikari.totalConnections").isNumber())
                .andExpect(jsonPath("$.data.hikari.activeConnections").isNumber());
    }

    @Test
    void dataSnapshotIsDisabledByDefaultWithEmptyEntries() throws Exception {
        mockMvc.perform(get("/admin/monitor/data")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hikari.maxPoolSize").isNumber())
                .andExpect(jsonPath("$.data.slowSql.enabled").value(false))
                .andExpect(jsonPath("$.data.slowSql.thresholdMs").value(500))
                .andExpect(jsonPath("$.data.slowSql.entries").isArray())
                .andExpect(jsonPath("$.data.slowSql.entries.length()").value(0));
    }

    @Test
    void theMonitorReadEndpointsRequireThePageGrant() throws Exception {
        mockMvc.perform(get("/admin/monitor/server")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/monitor/data")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
    }

    /** 只授 /monitor/server 页面：服务监控放行、数据监控仍 205（页面授予只开自己的读）。 */
    @Test
    void aPageGrantOpensOnlyItsOwnRead() throws Exception {
        Integer serverPageId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/monitor/server' AND type = 1 AND is_deleted = 0",
                Integer.class);
        assertTrue(serverPageId != null && serverPageId > 0, "V118 必须种下 /monitor/server 页面行");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, is_deleted) "
                + "VALUES (?, '服务监控只读', 'server_readonly', 0)", READ_ONLY_ROLE_ID);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                READ_ONLY_ROLE_ID, serverPageId);
        jdbcTemplate.update("INSERT INTO sys_user_role (role_id, user_id, is_deleted) VALUES (?, 2, 0)",
                READ_ONLY_ROLE_ID);

        mockMvc.perform(get("/admin/monitor/server")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cpu").exists());
        mockMvc.perform(get("/admin/monitor/data")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
    }
}
