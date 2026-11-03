package com.lautung.oa.monitor;

import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.auth.mapper.SysUserMapper;
import com.lautung.oa.common.jwt.JwtHelper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 慢 SQL 缓冲的数据监控端到端合同（ADR 0060）：采集开启（阈值 0，每条语句都慢）时，
 * 真实 mapper 查询经 {@code SlowSqlLoggingInterceptor} 进缓冲，数据监控读出最新在前的一行；
 * 条目 JSON 字段集合钉死为 {@code timestamp / statementId / elapsedMs / rows / sql}——
 * 没有参数字段是 ADR 0039 的安全边界，出现 params 键即红。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guigu_oa_slow_sql_data_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP",
        "lautung.oa.sql.slow-sql.enabled=true",
        "lautung.oa.sql.slow-sql.threshold-ms=0"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SlowSqlBufferDataContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @BeforeEach
    @AfterEach
    void resetFixtures() {
        ((InMemoryRedisTemplate) redisTemplate).clear();
        jdbcTemplate.update("UPDATE sys_user SET status = 1, is_deleted = 0, token_version = 0 "
                + "WHERE id IN (1, 2)");
    }

    @Test
    void realMapperQueryLandsInBufferAndTheEndpointReturnsExactFieldSet() throws Exception {
        sysUserMapper.selectById(1L);

        String body = mockMvc.perform(get("/admin/monitor/data")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.slowSql.enabled").value(true))
                .andExpect(jsonPath("$.data.slowSql.thresholdMs").value(0))
                .andExpect(jsonPath("$.data.slowSql.entries").isArray())
                .andExpect(jsonPath("$.data.hikari.maxPoolSize").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 阈值 0 时认证链的查询也是「慢」语句，缓冲不止一行；钉住的是目标语句在缓冲里
        // 且每一行字段集合一致（没有参数字段是安全边界，不是实现细节）。
        JsonNode entries = OBJECT_MAPPER.readTree(body).path("data").path("slowSql").path("entries");
        assertTrue(entries.size() >= 1, "至少应有目标查询一行: " + entries.size());
        boolean sawTarget = false;
        for (JsonNode entry : entries) {
            assertEquals(Set.of("timestamp", "statementId", "elapsedMs", "rows", "sql"),
                    Set.copyOf(entry.propertyNames()),
                    "缓冲条目字段集合钉死：出现参数字段即违反 ADR 0060/0039");
            assertTrue(entry.get("timestamp").asLong() > 0, "timestamp 是 epoch 毫秒");
            assertTrue(entry.get("sql").asString().length() <= 1024, "SQL 已截断到 1024");
            if ("com.lautung.oa.auth.mapper.SysUserMapper.selectById".equals(entry.get("statementId").asString())) {
                sawTarget = true;
            }
        }
        assertTrue(sawTarget, "mapper.selectById 必须出现在缓冲里");
    }

    /** 多条语句后缓冲仍满额可读；每行字段集合一致（顺序语义在 collector 单测钉住）。 */
    @Test
    void bufferStaysReadableAfterSeveralSlowStatements() throws Exception {
        for (int i = 1; i <= 3; i++) {
            sysUserMapper.selectById(1L);
        }

        String body = mockMvc.perform(get("/admin/monitor/data")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode entries = OBJECT_MAPPER.readTree(body).path("data").path("slowSql").path("entries");
        assertTrue(entries.size() >= 3, "至少记录了三次 mapper 查询: " + entries.size());
        for (JsonNode entry : entries) {
            assertEquals(Set.of("timestamp", "statementId", "elapsedMs", "rows", "sql"),
                    Set.copyOf(entry.propertyNames()));
        }
    }
}
