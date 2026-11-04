package com.lautung.oa.monitor;

import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.framework.observability.SlowSqlCollector;
import com.lautung.oa.monitor.service.PoolHistorySampler;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据监控统计段与连接池历史段的 HTTP 合同（#258）：慢 SQL 环按 statementId 聚合的
 * 口径（次数/平均/最大/最近时间）、200 环窗口边界、关闭态的统计空段、历史段的形状
 * 与 120 点上限。阈值调到很高，只让测试直接写入的条目进环，避免认证链查询干扰口径。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guigu_oa_data_statistics_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP",
        "lautung.oa.sql.slow-sql.enabled=true",
        "lautung.oa.sql.slow-sql.threshold-ms=999999"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class DataMonitorStatisticsContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> POOL_POINT_FIELDS = Set.of(
            "timestamp", "activeConnections", "idleConnections", "totalConnections", "threadsAwaiting");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private SlowSqlCollector collector;

    @Autowired
    private PoolHistorySampler poolHistorySampler;

    @BeforeEach
    @AfterEach
    void resetFixtures() {
        ((InMemoryRedisTemplate) redisTemplate).clear();
        ContractFixtures.resetSeededUsers(jdbcTemplate);
    }

    @Test
    void aggregatesTheRingByNameWithCountAverageMaxAndLatestTimestamp() throws Exception {
        collector.record("contract.alpha", 100L, 1L, "SELECT alpha");
        collector.record("contract.alpha", 200L, 1L, "SELECT alpha");
        collector.record("contract.alpha", 300L, 1L, "SELECT alpha");

        JsonNode stats = statisticsNode();

        assertTrue(stats.get("enabled").asBoolean(), "采集开启时统计段 enabled=true");
        assertEquals("只覆盖慢 SQL，不是全量 SQL", stats.get("note").asString(),
                "统计口径必须明示只覆盖慢 SQL");
        JsonNode alpha = rowOf(stats, "contract.alpha");
        assertNotNull(alpha, "statementId 必须成一行: " + stats.get("statements"));
        assertEquals(3, alpha.get("count").asInt());
        assertEquals(200.0, alpha.get("avgElapsedMs").asDouble(), 1e-9);
        assertEquals(300L, alpha.get("maxElapsedMs").asLong());
        assertTrue(alpha.get("lastTimestamp").asLong() > 0, "最近一条时间是 epoch 毫秒");
        assertEquals(Set.of("statementId", "count", "avgElapsedMs", "maxElapsedMs", "lastTimestamp"),
                Set.copyOf(alpha.propertyNames()), "统计行字段集合钉死");
    }

    /** 环满后只统计窗口内最近 200 条：先写 5 条 9999ms 再写 200 条 10ms，旧点被挤掉。 */
    @Test
    void windowBoundaryOnlyCountsTheMostRecent200RingEntries() throws Exception {
        for (int i = 0; i < 5; i++) {
            collector.record("contract.bulk", 9999L, 1L, "SELECT slow");
        }
        for (int i = 0; i < 200; i++) {
            collector.record("contract.bulk", 10L, 0L, "SELECT fast");
        }

        JsonNode bulk = rowOf(statisticsNode(), "contract.bulk");

        assertNotNull(bulk);
        assertEquals(200, bulk.get("count").asInt(), "窗口最多 200 条，早于窗口的 5 条不计");
        assertEquals(10.0, bulk.get("avgElapsedMs").asDouble(), 1e-9);
        assertEquals(10L, bulk.get("maxElapsedMs").asLong());
    }

    /** 历史段形状与上限：125 次采样后只留最近 120 点，字段集合与时间升序都钉住。 */
    @Test
    void poolHistoryKeepsTheMostRecent120PointsWithFixedShape() throws Exception {
        // 先走一次请求，确保 H2 的 Hikari 池已启动（池未启动时采样器不补 null 点）。
        statisticsNode();
        for (int i = 0; i < 125; i++) {
            poolHistorySampler.sample();
        }

        JsonNode history = OBJECT_MAPPER.readTree(mockMvc.perform(get("/admin/monitor/data")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poolHistory.capacity").value(120))
                .andExpect(jsonPath("$.data.poolHistory.intervalMs").value(30000))
                .andExpect(jsonPath("$.data.poolHistory.points").isArray())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("poolHistory");

        JsonNode points = history.path("points");
        assertEquals(120, points.size(), "最多保留 120 点");
        long previousTimestamp = Long.MIN_VALUE;
        for (JsonNode point : points) {
            assertEquals(POOL_POINT_FIELDS, Set.copyOf(point.propertyNames()),
                    "历史点字段集合钉死：active/idle/total/waiting 与时间");
            assertTrue(point.get("timestamp").asLong() >= previousTimestamp, "时间升序");
            previousTimestamp = point.get("timestamp").asLong();
        }
    }

    private JsonNode statisticsNode() throws Exception {
        String body = mockMvc.perform(get("/admin/monitor/data")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return OBJECT_MAPPER.readTree(body).path("data").path("sqlStatistics");
    }

    private static JsonNode rowOf(JsonNode statistics, String statementId) {
        for (JsonNode row : statistics.path("statements")) {
            if (statementId.equals(row.path("statementId").asString())) {
                return row;
            }
        }
        return null;
    }
}
