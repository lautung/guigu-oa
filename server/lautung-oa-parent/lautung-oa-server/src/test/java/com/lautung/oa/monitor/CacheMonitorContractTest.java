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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 缓存监控与缓存列表的 HTTP 合同（#237，ADR 0060）：INFO 五段解析成字段不整段回传、
 * SCAN 前缀白名单（空/未知 201）、逐键 TTL、游标翻页、单键删除与权限缓存清空只限 perm:、
 * 页面授予与 {@code monitor:cache:clean} 按钮点、权限缓存键迁移（写新键顺手删旧裸键）。
 *
 * <p>Redis 是测试内存实现（本票补齐 INFO 与 getExpire 能力）；键形状直接可观察。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_cache_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class CacheMonitorContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 一份真实形状的 INFO 原文：五段各取若干行，另带 os（不在回传字段里的原始键）。
     */
    private static final String INFO_TEXT = """
            # Server
            redis_version:7.2.4
            os:Linux 5.15.0 x86_64
            uptime_in_seconds:86400
            # Clients
            connected_clients:7
            # Memory
            used_memory:1048576
            used_memory_human:1.00M
            maxmemory:0
            # Stats
            keyspace_hits:120
            keyspace_misses:30
            expired_keys:5
            # Keyspace
            db0:keys=3,expires=1,avg_ttl=0
            db1:keys=10,expires=2,avg_ttl=5000
            """;

    private static final long READ_ONLY_ROLE_ID = 803L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    @AfterEach
    void resetFixtures() {
        redis().clear();
        ContractFixtures.resetSeededUsers(jdbcTemplate);
        ContractFixtures.deleteAdHocRole(jdbcTemplate, READ_ONLY_ROLE_ID);
    }

    @Test
    void infoParsesTheFiveSectionsIntoFieldsAndNeverEchoesRawSections() throws Exception {
        redis().setInfoText(INFO_TEXT);

        mockMvc.perform(get("/admin/monitor/cache")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // server 段
                .andExpect(jsonPath("$.data.server.redisVersion").value("7.2.4"))
                .andExpect(jsonPath("$.data.server.uptimeSeconds").value(86400))
                // memory 段
                .andExpect(jsonPath("$.data.memory.usedMemoryBytes").value(1048576))
                .andExpect(jsonPath("$.data.memory.usedMemoryHuman").value("1.00M"))
                .andExpect(jsonPath("$.data.memory.maxMemoryBytes").value(0))
                // clients 段
                .andExpect(jsonPath("$.data.clients.connectedClients").value(7))
                // stats 段
                .andExpect(jsonPath("$.data.stats.keyspaceHits").value(120))
                .andExpect(jsonPath("$.data.stats.keyspaceMisses").value(30))
                .andExpect(jsonPath("$.data.stats.expiredKeys").value(5))
                // keyspace 段：逐 db 解析
                .andExpect(jsonPath("$.data.keyspace.length()").value(2))
                .andExpect(jsonPath("$.data.keyspace[0].db").value("db0"))
                .andExpect(jsonPath("$.data.keyspace[0].keys").value(3))
                .andExpect(jsonPath("$.data.keyspace[0].expires").value(1))
                .andExpect(jsonPath("$.data.keyspace[0].avgTtlMs").value(0))
                .andExpect(jsonPath("$.data.keyspace[1].db").value("db1"))
                .andExpect(jsonPath("$.data.keyspace[1].keys").value(10))
                .andExpect(jsonPath("$.data.keyspace[1].expires").value(2))
                .andExpect(jsonPath("$.data.keyspace[1].avgTtlMs").value(5000))
                // 不整段回传：原始 INFO 键一律不外漏。
                .andExpect(jsonPath("$.data.redis_version").doesNotExist())
                .andExpect(jsonPath("$.data.used_memory").doesNotExist())
                .andExpect(jsonPath("$.data.os").doesNotExist())
                .andExpect(jsonPath("$.data.db0").doesNotExist());
    }

    @Test
    void infoDegradesToNullSectionsWhenRedisInfoIsUnavailable() throws Exception {
        mockMvc.perform(get("/admin/monitor/cache")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.server.redisVersion").doesNotExist())
                .andExpect(jsonPath("$.data.memory.usedMemoryBytes").doesNotExist())
                .andExpect(jsonPath("$.data.stats.keyspaceHits").doesNotExist())
                .andExpect(jsonPath("$.data.keyspace[0]").doesNotExist());
    }

    @Test
    void theKeysListRejectsMissingBlankAndUnknownPrefixes() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();

        // 空值与白名单外都按 201 拒绝。
        mockMvc.perform(get("/admin/monitor/cache/keys").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("不支持的前缀"));
        mockMvc.perform(get("/admin/monitor/cache/keys").param("prefix", " ")
                        .header("token", token))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("不支持的前缀"));
        for (String unknown : new String[]{"user:", "perm", "PERM:", "KEYS:", "login:"}) {
            mockMvc.perform(get("/admin/monitor/cache/keys").param("prefix", unknown)
                            .header("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.message").value("不支持的前缀"));
        }

        // 白名单六个前缀放行（即便还没有任何匹配键）。
        for (String allowed : new String[]{
                "perm:", "login:session:", "login:attempt:", "token:revoked:", "config:", "dict:"}) {
            mockMvc.perform(get("/admin/monitor/cache/keys").param("prefix", allowed)
                            .header("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.keys").isArray())
                    .andExpect(jsonPath("$.data.nextCursor").value(0));
        }
    }

    @Test
    void theKeysListOnlySurfacesKeysUnderTheRequestedPrefix() throws Exception {
        redis().opsForValue().set("admin", "[{\"authority\":\"legacy\"}]");
        redis().opsForValue().set("perm:cache:admin", "[{\"authority\":\"current\"}]");
        redis().opsForValue().set("token:revoked:s-1", "1");

        mockMvc.perform(get("/admin/monitor/cache/keys").param("prefix", "perm:")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.keys.length()").value(1))
                .andExpect(jsonPath("$.data.keys[0].key").value("perm:cache:admin"));

        mockMvc.perform(get("/admin/monitor/cache/keys").param("prefix", "token:revoked:")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(jsonPath("$.data.keys.length()").value(1))
                .andExpect(jsonPath("$.data.keys[0].key").value("token:revoked:s-1"));
    }

    @Test
    void theKeysListPaginatesSortedWithTtlPerKeyAndEndsTheCursor() throws Exception {
        for (int i = 1; i <= 5; i++) {
            redis().opsForValue().set("perm:cache:u" + i, "{}");
        }
        redis().expire("perm:cache:u1", java.time.Duration.ofSeconds(120));
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();

        MvcResult page1 = mockMvc.perform(get("/admin/monitor/cache/keys")
                        .param("prefix", "perm:").param("count", "2").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.keys.length()").value(2))
                .andExpect(jsonPath("$.data.keys[0].key").value("perm:cache:u1"))
                .andExpect(jsonPath("$.data.keys[1].key").value("perm:cache:u2"))
                .andExpect(jsonPath("$.data.nextCursor").value(2))
                .andReturn();
        JsonNode first = OBJECT_MAPPER.readTree(page1.getResponse().getContentAsString())
                .path("data").path("keys");
        long u1Ttl = first.get(0).get("ttl").asLong();
        assertTrue(u1Ttl > 0 && u1Ttl <= 120, "u1 的 TTL 应落在 (0,120]，实际 " + u1Ttl);
        assertEquals(-1L, first.get(1).get("ttl").asLong(), "无过期键的 TTL 是 -1");

        mockMvc.perform(get("/admin/monitor/cache/keys")
                        .param("prefix", "perm:").param("cursor", "2").param("count", "2")
                        .header("token", token))
                .andExpect(jsonPath("$.data.keys[0].key").value("perm:cache:u3"))
                .andExpect(jsonPath("$.data.keys[1].key").value("perm:cache:u4"))
                .andExpect(jsonPath("$.data.nextCursor").value(4));

        mockMvc.perform(get("/admin/monitor/cache/keys")
                        .param("prefix", "perm:").param("cursor", "4").param("count", "2")
                        .header("token", token))
                .andExpect(jsonPath("$.data.keys.length()").value(1))
                .andExpect(jsonPath("$.data.keys[0].key").value("perm:cache:u5"))
                .andExpect(jsonPath("$.data.nextCursor").value(0));

        // 越界游标给回空页而不是错误；count 缺省 100。
        mockMvc.perform(get("/admin/monitor/cache/keys")
                        .param("prefix", "perm:").param("cursor", "99").header("token", token))
                .andExpect(jsonPath("$.data.keys.length()").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value(0));
        mockMvc.perform(get("/admin/monitor/cache/keys")
                        .param("prefix", "perm:").param("cursor", "-1").header("token", token))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("游标不合法"));
        mockMvc.perform(get("/admin/monitor/cache/keys")
                        .param("prefix", "perm:").param("count", "0").header("token", token))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("count 不合法"));
        // 超上限同样 201（上限见 CacheMonitorService.MAX_KEY_COUNT）。
        mockMvc.perform(get("/admin/monitor/cache/keys")
                        .param("prefix", "perm:").param("count", "1001").header("token", token))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("count 不合法"));
    }

    @Test
    void infoWriteLandsOnThePrefixedKeyAndDeletesTheLegacyBareKey() throws Exception {
        // 存量形状：裸 username 键（ADR 0060 之前的权限缓存）。
        redis().opsForValue().set("admin", "[{\"authority\":\"stale\"}]");

        MvcResult info = mockMvc.perform(get("/admin/system/index/info")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode permissions = OBJECT_MAPPER.readTree(info.getResponse().getContentAsString())
                .path("data").path("permissions");

        // 写进 perm:cache:admin，内容是 /info 同一份权限；裸键顺手清掉。
        String cached = redis().opsForValue().get("perm:cache:admin");
        assertTrue(cached != null && cached.contains("authority"),
                "/info 必须把权限缓存写进 perm:cache:admin");
        for (JsonNode permission : permissions) {
            assertTrue(cached.contains(permission.asString()),
                    "缓存里应有 " + permission.asString());
        }
        assertFalse(redis().hasKey("admin"), "写入新键时必须顺手删除旧裸 username 键");
    }

    @Test
    void singleKeyDeleteOnlyAllowsThePermPrefix() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        redis().opsForValue().set("perm:cache:alice", "{}");
        redis().opsForValue().set("token:revoked:s-1", "1");
        redis().opsForValue().set("login:attempt:admin", "1");
        redis().opsForHash().putAll("login:session:s-1", java.util.Map.of("userId", "1"));
        redis().opsForValue().set("admin", "[{\"authority\":\"legacy\"}]");

        mockMvc.perform(delete("/admin/monitor/cache/keys").param("key", "perm:cache:alice")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertFalse(redis().hasKey("perm:cache:alice"));

        // 非白名单键（含三个运行时前缀与裸 username 键）一律 201 且原键不动。
        for (String forbidden : new String[]{
                "token:revoked:s-1", "login:attempt:admin", "login:session:s-1", "admin"}) {
            mockMvc.perform(delete("/admin/monitor/cache/keys").param("key", forbidden)
                            .header("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.message").value("只允许删除 perm: 前缀的键"));
            assertTrue(redis().hasKey(forbidden), forbidden + " 不允许被删除");
        }

        mockMvc.perform(delete("/admin/monitor/cache/keys").param("key", "perm:cache:ghost")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("键不存在"));
        mockMvc.perform(delete("/admin/monitor/cache/keys").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只允许删除 perm: 前缀的键"));
    }

    @Test
    void clearingThePermissionCacheRemovesOnlyPermCacheKeys() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        redis().opsForValue().set("perm:cache:alice", "{}");
        redis().opsForValue().set("perm:cache:bob", "{}");
        redis().opsForValue().set("perm:other:note", "keep");
        redis().opsForValue().set("token:revoked:s-1", "1");
        redis().opsForValue().set("login:attempt:admin", "1");
        redis().opsForHash().putAll("login:session:s-1", java.util.Map.of("userId", "1"));

        mockMvc.perform(delete("/admin/monitor/cache/perm").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(2));

        assertFalse(redis().hasKey("perm:cache:alice"));
        assertFalse(redis().hasKey("perm:cache:bob"));
        for (String keep : new String[]{"perm:other:note", "token:revoked:s-1",
                "login:attempt:admin", "login:session:s-1"}) {
            assertTrue(redis().hasKey(keep), keep + " 不在清理范围内");
        }
    }

    @Test
    void theCleanActionsWriteOperLogRows() throws Exception {
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        redis().opsForValue().set("perm:cache:alice", "{}");

        mockMvc.perform(delete("/admin/monitor/cache/keys").param("key", "perm:cache:alice")
                        .header("token", token))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/admin/monitor/cache/perm").header("token", token))
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(1, count("SELECT COUNT(*) FROM sys_oper_log WHERE title = '缓存列表·删除键'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_oper_log WHERE title = '缓存列表·清空权限缓存'"));
    }

    @Test
    void theCacheEndpointsRequireTheirPagesAndTheCleanButtonPoint() throws Exception {
        String roleless = jwtHelper.createTokenPair(2L, "user", 0L).token();
        mockMvc.perform(get("/admin/monitor/cache").header("token", roleless))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/monitor/cache/keys").param("prefix", "perm:")
                        .header("token", roleless))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(delete("/admin/monitor/cache/keys").param("key", "perm:cache:x")
                        .header("token", roleless))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(delete("/admin/monitor/cache/perm").header("token", roleless))
                .andExpect(jsonPath("$.code").value(205));

        // 只授缓存监控页：只开 /admin/monitor/cache 的读。
        grantPage(READ_ONLY_ROLE_ID, "/monitor/cache");
        String cachePageOnly = jwtHelper.createTokenPair(2L, "user", 0L).token();
        mockMvc.perform(get("/admin/monitor/cache").header("token", cachePageOnly))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/monitor/cache/keys").param("prefix", "perm:")
                        .header("token", cachePageOnly))
                .andExpect(jsonPath("$.code").value(205));

        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", READ_ONLY_ROLE_ID);

        // 只授缓存列表页：列表读放行，两个清理动作仍 205。
        grantPage(READ_ONLY_ROLE_ID, "/monitor/cacheList");
        String listPageOnly = jwtHelper.createTokenPair(2L, "user", 0L).token();
        mockMvc.perform(get("/admin/monitor/cache/keys").param("prefix", "perm:")
                        .header("token", listPageOnly))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/monitor/cache").header("token", listPageOnly))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(delete("/admin/monitor/cache/keys").param("key", "perm:cache:x")
                        .header("token", listPageOnly))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(delete("/admin/monitor/cache/perm").header("token", listPageOnly))
                .andExpect(jsonPath("$.code").value(205));

        // 补授按钮点：清理放行。
        Integer buttonId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = 'monitor:cache:clean' AND type = 2 AND is_deleted = 0",
                Integer.class);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                READ_ONLY_ROLE_ID, buttonId);
        redis().opsForValue().set("perm:cache:alice", "{}");
        String cleaner = jwtHelper.createTokenPair(2L, "user", 0L).token();
        mockMvc.perform(delete("/admin/monitor/cache/keys").param("key", "perm:cache:alice")
                        .header("token", cleaner))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/admin/monitor/cache/perm").header("token", cleaner))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void v119SeedsTheTwoCachePagesAndTheCleanButtonUnderCacheList() {
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = '/monitor/cache' AND c.type = 1 "
                + "AND c.component = 'monitor/cache/index' AND p.path = '/monitor' AND p.type = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = '/monitor/cacheList' AND c.type = 1 "
                + "AND c.component = 'monitor/cacheList/index' AND p.path = '/monitor' AND p.type = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu b JOIN sys_menu p ON p.id = b.parent_id "
                + "WHERE b.type = 2 AND b.is_deleted = 0 AND p.path = '/monitor/cacheList' "
                + "AND b.perms = 'monitor:cache:clean'"));
        // ADR 0056/0060：缓存监控是只读页，不设按钮点。
        assertEquals(0, count("SELECT COUNT(*) FROM sys_menu b JOIN sys_menu p ON p.id = b.parent_id "
                + "WHERE b.type = 2 AND b.is_deleted = 0 AND p.path = '/monitor/cache'"));
        assertEquals(3, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id "
                + "JOIN sys_menu m ON m.id = rm.menu_id "
                + "WHERE r.role_code = 'super_admin' AND rm.is_deleted = 0 "
                + "AND (m.path IN ('/monitor/cache', '/monitor/cacheList') "
                + "OR m.perms = 'monitor:cache:clean')"));
    }

    private void grantPage(long roleId, String menuPath) {
        if (count("SELECT COUNT(*) FROM sys_role WHERE id = " + roleId) == 0) {
            jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, is_deleted) "
                    + "VALUES (?, '缓存监控只读', 'cache_readonly', 0)", roleId);
            jdbcTemplate.update("INSERT INTO sys_user_role (role_id, user_id, is_deleted) VALUES (?, 2, 0)",
                    roleId);
        }
        Integer menuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = ? AND type = 1 AND is_deleted = 0",
                Integer.class, menuPath);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                roleId, menuId);
    }

    private InMemoryRedisTemplate redis() {
        return (InMemoryRedisTemplate) redisTemplate;
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
