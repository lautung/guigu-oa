package com.lautung.oa.config;

import com.jayway.jsonpath.JsonPath;
import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.config.service.ConfigReader;
import com.lautung.oa.support.InMemoryRedisTemplate;
import db.migration.V127__SysConfigSchemaAndMenu;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #252 的参数设置合同：V127 建表/页面/按钮/注册开关种子的幂等、CRUD 与筛选、
 * 内置项保护（不可删、不可改 key、只可改 value）、缓存写后失效与手动刷新、
 * 缓存列表白名单可见但不可清理、auth 只读端口取值、页面/按钮 205 矩阵。
 *
 * <p>夹具统一 {@code t252} / {@code T252} 前缀；Redis 用内存实现，键形状可直接观察。
 * 所有拒绝用例都用良构体，避免校验先于 gate 的顺序把断言带偏（ADR 0034）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_sys_config;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SysConfigContractTest {

    private static final String REGISTER_SWITCH = "sys.account.registerUser";
    private static final String REGISTER_CACHE_KEY = "config:" + REGISTER_SWITCH;
    private static final long PAGE_ONLY_ROLE_ID = 2520L;
    private static final long PAGE_ONLY_USER_ID = 2520L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private ConfigReader configReader;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    @AfterEach
    void resetFixtures() {
        redis().clear();
        jdbcTemplate.update("UPDATE sys_config SET config_value = 'false' WHERE config_key = ?",
                REGISTER_SWITCH);
        jdbcTemplate.update("DELETE FROM sys_config WHERE builtin = 0");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ? OR role_id = ?",
                PAGE_ONLY_USER_ID, PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", PAGE_ONLY_USER_ID);
    }

    @Test
    void v127SeedsSchemaMenuAndTheRegisterSwitchIdempotently() throws Exception {
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version = '127' AND success = TRUE"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_config WHERE config_key = '"
                + REGISTER_SWITCH + "' AND is_deleted = 0"));
        assertEquals("false", jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = ?", String.class, REGISTER_SWITCH));
        assertEquals("boolean", jdbcTemplate.queryForObject(
                "SELECT value_type FROM sys_config WHERE config_key = ?", String.class, REGISTER_SWITCH));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT builtin FROM sys_config WHERE config_key = ?", Integer.class, REGISTER_SWITCH));

        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = '/system/config' AND c.type = 1 "
                + "AND c.component = 'system/config/index' AND COALESCE(c.perms, '') = '' "
                + "AND p.path = '/system' AND p.type = 0 AND c.is_deleted = 0"));
        assertEquals(3, count("SELECT COUNT(*) FROM sys_menu b JOIN sys_menu p ON p.id = b.parent_id "
                + "WHERE b.type = 2 AND b.is_deleted = 0 AND p.path = '/system/config' "
                + "AND b.perms IN ('system:config:add','system:config:edit','system:config:remove') "
                + "AND COALESCE(b.path, '') = '' AND COALESCE(b.component, '') = ''"));
        assertEquals(4, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id "
                + "JOIN sys_menu m ON m.id = rm.menu_id "
                + "WHERE r.role_code = 'super_admin' AND rm.is_deleted = 0 "
                + "AND (m.path = '/system/config' "
                + "OR m.perms IN ('system:config:add','system:config:edit','system:config:remove'))"));

        int menus = count("SELECT COUNT(*) FROM sys_menu");
        int configs = count("SELECT COUNT(*) FROM sys_config");
        try (Connection connection = dataSource.getConnection()) {
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V127__SysConfigSchemaAndMenu().migrate(context);
            new V127__SysConfigSchemaAndMenu().migrate(context);
        }
        assertEquals(menus, count("SELECT COUNT(*) FROM sys_menu"));
        assertEquals(configs, count("SELECT COUNT(*) FROM sys_config"));
    }

    @Test
    void theRegisterSwitchIsReadableThroughTheConfigPort() {
        assertEquals("false", configReader.getValue(REGISTER_SWITCH));
        assertFalse(configReader.getBoolean(REGISTER_SWITCH, true));
        assertTrue(Boolean.TRUE.equals(redis().hasKey(REGISTER_CACHE_KEY)),
                "读端口应把值写进 config: 前缀的缓存键");

        assertNull(configReader.getValue("t252.missing"));
        assertTrue(configReader.getBoolean("t252.missing", true));
        assertFalse(Boolean.TRUE.equals(redis().hasKey("config:t252.missing")),
                "不存在的参数不缓存空值");
    }

    @Test
    void configCrudPagesFiltersAndRejectsDuplicatesAndBadTypes() throws Exception {
        long id = saveConfig("t252_custom", "T252参数", "value-1", "string", "T252备注");

        mockMvc.perform(get("/admin/system/config/1/10?configKey=t252").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) id))
                .andExpect(jsonPath("$.data.records[0].configKey").value("t252_custom"))
                .andExpect(jsonPath("$.data.records[0].configName").value("T252参数"))
                .andExpect(jsonPath("$.data.records[0].configValue").value("value-1"))
                .andExpect(jsonPath("$.data.records[0].valueType").value("string"))
                .andExpect(jsonPath("$.data.records[0].builtin").value(0))
                .andExpect(jsonPath("$.data.records[0].remark").value("T252备注"));

        mockMvc.perform(get("/admin/system/config/1/10?configName=T252").header("token", adminToken()))
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/admin/system/config/1/10?configName=不存在").header("token", adminToken()))
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(put("/admin/system/config/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"configValue\":\"value-2\","
                                + "\"configName\":\"T252参数改名\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("value-2", jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE id = ?", String.class, id));
        assertEquals("T252参数改名", jdbcTemplate.queryForObject(
                "SELECT config_name FROM sys_config WHERE id = ?", String.class, id));

        mockMvc.perform(post("/admin/system/config/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"t252_custom\",\"configName\":\"重复\","
                                + "\"configValue\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("参数键已存在"));

        mockMvc.perform(post("/admin/system/config/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"t252_bad_type\",\"configName\":\"坏类型\","
                                + "\"valueType\":\"json\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("参数类型不合法"));

        mockMvc.perform(post("/admin/system/config/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"t252_bad_bool\",\"configName\":\"坏布尔\","
                                + "\"valueType\":\"boolean\",\"configValue\":\"maybe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("布尔类型的参数值只能是 true 或 false"));

        mockMvc.perform(delete("/admin/system/config/remove/" + id).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_config WHERE id = " + id
                + " AND is_deleted = 0"));

        // 软删行让出 config_key，同一键可以重新创建（唯一索引仍然生效）。
        saveConfig("t252_custom", "T252参数重建", "value-3", "string", "");
    }

    @Test
    void builtinItemsCannotBeDeletedOrRenamedButTheirValueChanges() throws Exception {
        long id = registerSwitchId();

        mockMvc.perform(put("/admin/system/config/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"configValue\":\"true\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("true", jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE id = ?", String.class, id));
        assertTrue(configReader.getBoolean(REGISTER_SWITCH, false),
                "写入后缓存失效，读端口应看到新值");

        mockMvc.perform(put("/admin/system/config/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"configKey\":\"t252.hijack\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("内置参数的键不能修改"));
        assertEquals(REGISTER_SWITCH, jdbcTemplate.queryForObject(
                "SELECT config_key FROM sys_config WHERE id = ?", String.class, id));

        mockMvc.perform(put("/admin/system/config/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"configName\":\"改名\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("内置参数只允许修改参数值"));

        mockMvc.perform(delete("/admin/system/config/remove/" + id).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("内置参数不能删除"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_config WHERE id = " + id + " AND is_deleted = 0"));
    }

    @Test
    void writesInvalidateTheCacheAndManualRefreshRebuildsIt() throws Exception {
        long id = registerSwitchId();
        assertFalse(configReader.getBoolean(REGISTER_SWITCH, true));
        assertTrue(Boolean.TRUE.equals(redis().hasKey(REGISTER_CACHE_KEY)));

        // 绕过服务直接改库：缓存命中时读端口仍回答旧值，证明读路径真的走了缓存。
        jdbcTemplate.update("UPDATE sys_config SET config_value = 'true' WHERE id = ?", id);
        assertFalse(configReader.getBoolean(REGISTER_SWITCH, true));

        MvcResult refreshed = mockMvc.perform(post("/admin/system/config/refresh")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Number cleared = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.data");
        assertTrue(cleared.longValue() >= 1, "手动刷新应清掉至少一个 config: 键");
        assertFalse(Boolean.TRUE.equals(redis().hasKey(REGISTER_CACHE_KEY)));
        assertTrue(configReader.getBoolean(REGISTER_SWITCH, false));

        // 走接口写回 false：写后立即失效，读端口不需要等手动刷新。
        mockMvc.perform(put("/admin/system/config/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"configValue\":\"false\"}"))
                .andExpect(jsonPath("$.code").value(200));
        assertFalse(Boolean.TRUE.equals(redis().hasKey(REGISTER_CACHE_KEY)));
        assertFalse(configReader.getBoolean(REGISTER_SWITCH, true));
    }

    @Test
    void theConfigPrefixIsVisibleInTheCacheListButNotDeletable() throws Exception {
        redis().opsForValue().set("config:t252.manual", "true");
        redis().opsForValue().set("perm:cache:admin", "[]");
        String token = adminToken();

        mockMvc.perform(get("/admin/monitor/cache/keys").param("prefix", "config:")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.keys.length()").value(1))
                .andExpect(jsonPath("$.data.keys[0].key").value("config:t252.manual"));

        mockMvc.perform(delete("/admin/monitor/cache/keys").param("key", "config:t252.manual")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只允许删除 perm: 前缀的键"));
        assertTrue(Boolean.TRUE.equals(redis().hasKey("config:t252.manual")));

        mockMvc.perform(delete("/admin/monitor/cache/perm").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));
        assertTrue(Boolean.TRUE.equals(redis().hasKey("config:t252.manual")),
                "清空权限缓存不能碰 config: 前缀");
    }

    @Test
    void configRoutesGateByPageAndButtons() throws Exception {
        long configId = saveConfig("t252_gate", "T252网关", "x", "string", "");
        String plainToken = userToken();

        mockMvc.perform(get("/admin/system/config/1/10").header("token", plainToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(post("/admin/system/config/save").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"t252_none\",\"configName\":\"无权限\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(put("/admin/system/config/update").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + configId + ",\"configValue\":\"y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(delete("/admin/system/config/remove/" + configId)
                        .header("token", plainToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(post("/admin/system/config/refresh").header("token", plainToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        insertPageOnlyPrincipal();
        String pageToken = jwtHelper.createTokenPair(PAGE_ONLY_USER_ID, "t252-page", 0L).token();
        mockMvc.perform(get("/admin/system/config/1/10").header("token", pageToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/config/save").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"t252_pageonly\",\"configName\":\"只授页面\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(post("/admin/system/config/refresh").header("token", pageToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        grantButton("system:config:add");
        grantButton("system:config:edit");
        grantButton("system:config:remove");
        mockMvc.perform(post("/admin/system/config/save").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"t252_pageplus\",\"configName\":\"页面加按钮\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/index/info").header("token", adminToken()))
                .andExpect(jsonPath("$.data.permissions", hasItems(
                        "system:config:add", "system:config:edit", "system:config:remove")))
                .andExpect(jsonPath("$.data.menus[*].path", hasItems("/system/config")));
    }

    @Test
    void blankSaveIsRejectedWithFieldMessages() throws Exception {
        mockMvc.perform(post("/admin/system/config/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"\",\"configName\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(containsString("参数键不能为空")))
                .andExpect(jsonPath("$.message").value(containsString("参数名称不能为空")));
    }

    private long saveConfig(String key, String name, String value, String type, String remark)
            throws Exception {
        mockMvc.perform(post("/admin/system/config/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"" + key + "\",\"configName\":\"" + name
                                + "\",\"configValue\":\"" + value + "\",\"valueType\":\"" + type
                                + "\",\"remark\":\"" + remark + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_config WHERE config_key = ? AND is_deleted = 0", Long.class, key);
    }

    private long registerSwitchId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_config WHERE config_key = ? AND is_deleted = 0",
                Long.class, REGISTER_SWITCH);
    }

    private void insertPageOnlyPrincipal() {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, 'T252角色', 't252_role', 1, 0)", PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, is_deleted) "
                + "VALUES (?, 't252-page', '', 'T252用户', 1, 0)", PAGE_ONLY_USER_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                PAGE_ONLY_USER_ID, PAGE_ONLY_ROLE_ID);
        grantMenu(menuIdByPath("/system/config"));
    }

    private void grantButton(String perms) {
        grantMenu(menuIdByPerms(perms));
    }

    private void grantMenu(long menuId) {
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                PAGE_ONLY_ROLE_ID, menuId);
    }

    private long menuIdByPath(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = ? AND is_deleted = 0", Long.class, path);
    }

    private long menuIdByPerms(String perms) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND is_deleted = 0", Long.class, perms);
    }

    private String adminToken() {
        return jwtHelper.createTokenPair(1L, "admin", 0L).token();
    }

    private String userToken() {
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'user'", Long.class);
        return jwtHelper.createTokenPair(userId, "user", 0L).token();
    }

    private InMemoryRedisTemplate redis() {
        return (InMemoryRedisTemplate) redisTemplate;
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
