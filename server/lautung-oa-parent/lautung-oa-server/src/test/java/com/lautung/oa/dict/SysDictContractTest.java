package com.lautung.oa.dict;

import com.jayway.jsonpath.JsonPath;
import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.support.InMemoryRedisTemplate;
import db.migration.V130__SysDictSchemaAndMenu;
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
 * #251 的字典管理合同：V130 建表/页面/按钮/四组种子的幂等、两级 CRUD、
 * 有数据拒删、缓存写后失效与手动刷新、{@code dict:} 在缓存列表可见但不可清理、
 * 标签读（单类型/批量）登录即有、页面/按钮 205 矩阵。
 *
 * <p>夹具统一 {@code t251} 前缀；Redis 用内存实现，键形状可直接观察。
 * 所有拒绝用例都用良构体，避免校验先于 gate 的顺序把断言带偏（ADR 0034）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_sys_dict;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SysDictContractTest {

    private static final String NOTICE_TYPE = "sys_notice_type";
    private static final long PAGE_ONLY_ROLE_ID = 2510L;
    private static final long PAGE_ONLY_USER_ID = 2510L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    @AfterEach
    void resetFixtures() {
        redis().clear();
        jdbcTemplate.update("DELETE FROM sys_dict_data WHERE dict_type LIKE 't251%'");
        jdbcTemplate.update("DELETE FROM sys_dict_data WHERE dict_type LIKE '__deleted__%'");
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE dict_type LIKE 't251%'");
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE dict_type LIKE '__deleted__%'");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ? OR role_id = ?",
                PAGE_ONLY_USER_ID, PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", PAGE_ONLY_USER_ID);
    }

    @Test
    void v130SeedsSchemaMenuAndFourDictionaryGroupsIdempotently() throws Exception {
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version = '126' AND success = TRUE"));

        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = '/system/dict' AND c.type = 1 "
                + "AND c.component = 'system/dict/index' AND COALESCE(c.perms, '') = '' "
                + "AND p.path = '/system' AND p.type = 0 AND c.is_deleted = 0"));
        assertEquals(3, count("SELECT COUNT(*) FROM sys_menu b JOIN sys_menu p ON p.id = b.parent_id "
                + "WHERE b.type = 2 AND b.is_deleted = 0 AND p.path = '/system/dict' "
                + "AND b.perms IN ('system:dict:add','system:dict:edit','system:dict:remove') "
                + "AND COALESCE(b.path, '') = '' AND COALESCE(b.component, '') = ''"));
        assertEquals(4, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id "
                + "JOIN sys_menu m ON m.id = rm.menu_id "
                + "WHERE r.role_code = 'super_admin' AND rm.is_deleted = 0 "
                + "AND (m.path = '/system/dict' "
                + "OR m.perms IN ('system:dict:add','system:dict:edit','system:dict:remove'))"));

        assertEquals(4, count("SELECT COUNT(*) FROM sys_dict_type WHERE dict_type IN "
                + "('sys_notice_type','sys_notice_status','sys_job_status','sys_login_status') "
                + "AND status = 1 AND is_deleted = 0"));
        assertEquals(12, count("SELECT COUNT(*) FROM sys_dict_data WHERE dict_type IN "
                + "('sys_notice_type','sys_notice_status','sys_job_status','sys_login_status') "
                + "AND status = 1 AND is_deleted = 0"));
        assertEquals(2, count("SELECT COUNT(*) FROM sys_dict_data "
                + "WHERE dict_type = 'sys_notice_type' AND dict_value IN ('1','2')"));
        assertEquals("通知", jdbcTemplate.queryForObject(
                "SELECT dict_label FROM sys_dict_data WHERE dict_type = ? AND dict_value = '1'",
                String.class, NOTICE_TYPE));
        assertEquals("已撤回", jdbcTemplate.queryForObject(
                "SELECT dict_label FROM sys_dict_data "
                        + "WHERE dict_type = 'sys_notice_status' AND dict_value = '2'",
                String.class));
        assertEquals("跳过", jdbcTemplate.queryForObject(
                "SELECT dict_label FROM sys_dict_data "
                        + "WHERE dict_type = 'sys_job_status' AND dict_value = '3'",
                String.class));
        assertEquals("解锁", jdbcTemplate.queryForObject(
                "SELECT dict_label FROM sys_dict_data "
                        + "WHERE dict_type = 'sys_login_status' AND dict_value = '3'",
                String.class));

        int menus = count("SELECT COUNT(*) FROM sys_menu");
        int types = count("SELECT COUNT(*) FROM sys_dict_type");
        int data = count("SELECT COUNT(*) FROM sys_dict_data");
        try (Connection connection = dataSource.getConnection()) {
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V130__SysDictSchemaAndMenu().migrate(context);
            new V130__SysDictSchemaAndMenu().migrate(context);
        }
        assertEquals(menus, count("SELECT COUNT(*) FROM sys_menu"));
        assertEquals(types, count("SELECT COUNT(*) FROM sys_dict_type"));
        assertEquals(data, count("SELECT COUNT(*) FROM sys_dict_data"));
    }

    @Test
    void typeCrudPagesFiltersAndRejectsDuplicateNameOrCode() throws Exception {
        long id = saveType("t251_type", "T251类型", 1, "T251备注");

        mockMvc.perform(get("/admin/system/dict/type/1/10?dictType=t251").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) id))
                .andExpect(jsonPath("$.data.records[0].dictName").value("T251类型"))
                .andExpect(jsonPath("$.data.records[0].dictType").value("t251_type"))
                .andExpect(jsonPath("$.data.records[0].status").value(1))
                .andExpect(jsonPath("$.data.records[0].remark").value("T251备注"));

        mockMvc.perform(get("/admin/system/dict/type/1/10?dictName=T251").header("token", adminToken()))
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/admin/system/dict/type/1/10?dictName=不存在").header("token", adminToken()))
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(put("/admin/system/dict/type/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"dictName\":\"T251类型改名\","
                                + "\"status\":0,\"remark\":\"改过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("T251类型改名", jdbcTemplate.queryForObject(
                "SELECT dict_name FROM sys_dict_type WHERE id = ?", String.class, id));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT status FROM sys_dict_type WHERE id = ?", Integer.class, id));

        mockMvc.perform(post("/admin/system/dict/type/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"T251类型改名\",\"dictType\":\"t251_other\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("字典名称已存在"));
        mockMvc.perform(post("/admin/system/dict/type/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"T251另一类型\",\"dictType\":\"t251_type\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("字典类型已存在"));

        mockMvc.perform(post("/admin/system/dict/type/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"T251坏状态\",\"dictType\":\"t251_bad\",\"status\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("字典状态不合法"));

        mockMvc.perform(delete("/admin/system/dict/type/remove/" + id).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_dict_type WHERE id = " + id
                + " AND is_deleted = 0"));

        // 软删行让出 dict_name/dict_type，同一编码可以重新创建（唯一索引仍然生效）。
        saveType("t251_type", "T251类型重建", 1, "");
    }

    @Test
    void dataCrudSortsBySortValueAndDisabledRowsDoNotRender() throws Exception {
        saveType("t251_type", "T251类型", 1, "");
        long third = saveData("t251_type", "T251丙", "3", 30, 1, "");
        long first = saveData("t251_type", "T251甲", "1", 10, 1, "");
        long second = saveData("t251_type", "T251乙", "2", 20, 0, "停用");

        mockMvc.perform(get("/admin/system/dict/data/1/10?dictType=t251_type")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].dictValue").value("1"))
                .andExpect(jsonPath("$.data.records[0].sortValue").value(10))
                .andExpect(jsonPath("$.data.records[1].dictValue").value("2"))
                .andExpect(jsonPath("$.data.records[2].dictValue").value("3"));

        // 读标签只回启用行，按 sort_value 升序；未命中类型回空数组。
        mockMvc.perform(get("/admin/system/dict/data/type/t251_type").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].dictLabel").value("T251甲"))
                .andExpect(jsonPath("$.data[0].dictValue").value("1"))
                .andExpect(jsonPath("$.data[1].dictLabel").value("T251丙"));
        mockMvc.perform(get("/admin/system/dict/data/type/t251_missing").header("token", adminToken()))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(post("/admin/system/dict/data/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"t251_type\",\"dictLabel\":\"重复值\","
                                + "\"dictValue\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("字典值已存在"));

        mockMvc.perform(put("/admin/system/dict/data/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + second + ",\"status\":1,\"dictLabel\":\"T251乙启用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/system/dict/data/type/t251_type").header("token", adminToken()))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[1].dictLabel").value("T251乙启用"));

        mockMvc.perform(delete("/admin/system/dict/data/remove/" + third)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/system/dict/data/type/t251_type").header("token", adminToken()))
                .andExpect(jsonPath("$.data.length()").value(2));
        assertTrue(first > 0);
        assertTrue(second > 0);
    }

    @Test
    void deletingATypeWithDataIsRejectedAndDoesNotCascade() throws Exception {
        long typeId = saveType("t251_type", "T251类型", 1, "");
        long dataId = saveData("t251_type", "T251甲", "1", 1, 1, "");

        mockMvc.perform(delete("/admin/system/dict/type/remove/" + typeId)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("字典类型下仍有数据，不能删除"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_dict_type WHERE id = " + typeId
                + " AND is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_dict_data WHERE id = " + dataId
                + " AND is_deleted = 0"));

        mockMvc.perform(delete("/admin/system/dict/data/remove/" + dataId)
                        .header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/admin/system/dict/type/remove/" + typeId)
                        .header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void writesInvalidateTheCacheAndManualRefreshRebuildsIt() throws Exception {
        saveType("t251_type", "T251类型", 1, "");
        long dataId = saveData("t251_type", "T251甲", "1", 1, 1, "");
        String cacheKey = "dict:t251_type";

        mockMvc.perform(get("/admin/system/dict/data/type/t251_type").header("token", adminToken()))
                .andExpect(jsonPath("$.data.length()").value(1));
        assertTrue(Boolean.TRUE.equals(redis().hasKey(cacheKey)),
                "标签读应把结果写进 dict: 前缀的缓存键");

        // 绕过服务直接改库：缓存命中时读仍回答旧标签，证明读路径真的走了缓存。
        jdbcTemplate.update("UPDATE sys_dict_data SET dict_label = 'T251库改' WHERE id = ?", dataId);
        mockMvc.perform(get("/admin/system/dict/data/type/t251_type").header("token", adminToken()))
                .andExpect(jsonPath("$.data[0].dictLabel").value("T251甲"));

        MvcResult refreshed = mockMvc.perform(post("/admin/system/dict/refresh")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Number cleared = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.data");
        assertTrue(cleared.longValue() >= 1, "手动刷新应清掉至少一个 dict: 键");
        assertFalse(Boolean.TRUE.equals(redis().hasKey(cacheKey)));
        mockMvc.perform(get("/admin/system/dict/data/type/t251_type").header("token", adminToken()))
                .andExpect(jsonPath("$.data[0].dictLabel").value("T251库改"));

        // 走接口写回：写后立即失效，读不需要等手动刷新。
        mockMvc.perform(put("/admin/system/dict/data/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + dataId + ",\"dictLabel\":\"T251接口改\"}"))
                .andExpect(jsonPath("$.code").value(200));
        assertFalse(Boolean.TRUE.equals(redis().hasKey(cacheKey)));
        mockMvc.perform(get("/admin/system/dict/data/type/t251_type").header("token", adminToken()))
                .andExpect(jsonPath("$.data[0].dictLabel").value("T251接口改"));

        // 批量读一次取两组，未定义类型也回空数组。
        mockMvc.perform(get("/admin/system/dict/data/batch")
                        .param("types", "t251_type,sys_login_status,t251_missing")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.t251_type[0].dictLabel").value("T251接口改"))
                .andExpect(jsonPath("$.data.sys_login_status.length()").value(4))
                .andExpect(jsonPath("$.data.t251_missing.length()").value(0));
    }

    @Test
    void theDictPrefixIsVisibleInTheCacheListButNotDeletable() throws Exception {
        redis().opsForValue().set("dict:sys_notice_type", "[]");
        redis().opsForValue().set("perm:cache:admin", "[]");
        String token = adminToken();

        mockMvc.perform(get("/admin/monitor/cache/keys").param("prefix", "dict:")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.keys.length()").value(1))
                .andExpect(jsonPath("$.data.keys[0].key").value("dict:sys_notice_type"));

        mockMvc.perform(delete("/admin/monitor/cache/keys").param("key", "dict:sys_notice_type")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只允许删除 perm: 前缀的键"));
        assertTrue(Boolean.TRUE.equals(redis().hasKey("dict:sys_notice_type")));

        mockMvc.perform(delete("/admin/monitor/cache/perm").header("token", token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));
        assertTrue(Boolean.TRUE.equals(redis().hasKey("dict:sys_notice_type")),
                "清空权限缓存不能碰 dict: 前缀");
    }

    @Test
    void labelReadsAreOpenToEverySignedInUserWhileManagementReadsNeedThePage() throws Exception {
        String plainToken = userToken();

        mockMvc.perform(get("/admin/system/dict/type/1/10").header("token", plainToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(get("/admin/system/dict/data/1/10?dictType=" + NOTICE_TYPE)
                        .header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205));

        // 标签读不设页面授予：公告/日志页面按值渲染标签不需要字典管理权限。
        mockMvc.perform(get("/admin/system/dict/data/type/" + NOTICE_TYPE)
                        .header("token", plainToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].dictLabel").value("公告"));
        mockMvc.perform(get("/admin/system/dict/data/batch")
                        .param("types", NOTICE_TYPE)
                        .header("token", plainToken))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data." + NOTICE_TYPE + "[0].dictLabel").value("通知"));
    }

    @Test
    void dictRoutesGateByPageAndButtons() throws Exception {
        long typeId = saveType("t251_type", "T251类型", 1, "");
        long dataId = saveData("t251_type", "T251甲", "1", 1, 1, "");
        String plainToken = userToken();

        mockMvc.perform(post("/admin/system/dict/type/save").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"无权限类型\",\"dictType\":\"t251_none\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(put("/admin/system/dict/type/update").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + typeId + ",\"dictName\":\"改名\"}"))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(delete("/admin/system/dict/type/remove/" + typeId)
                        .header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(post("/admin/system/dict/data/save").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"t251_type\",\"dictLabel\":\"无权限\","
                                + "\"dictValue\":\"9\"}"))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(put("/admin/system/dict/data/update").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + dataId + ",\"dictLabel\":\"改名\"}"))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(delete("/admin/system/dict/data/remove/" + dataId)
                        .header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(post("/admin/system/dict/refresh").header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        insertPageOnlyPrincipal();
        String pageToken = jwtHelper.createTokenPair(PAGE_ONLY_USER_ID, "t251-page", 0L).token();
        mockMvc.perform(get("/admin/system/dict/type/1/10").header("token", pageToken))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/system/dict/data/1/10?dictType=t251_type")
                        .header("token", pageToken))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/dict/type/save").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"只授页面\",\"dictType\":\"t251_pageonly\"}"))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(post("/admin/system/dict/refresh").header("token", pageToken))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        grantButton("system:dict:add");
        grantButton("system:dict:edit");
        grantButton("system:dict:remove");
        mockMvc.perform(post("/admin/system/dict/type/save").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"页面加按钮\",\"dictType\":\"t251_pageplus\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/dict/refresh").header("token", pageToken))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/index/info").header("token", adminToken()))
                .andExpect(jsonPath("$.data.permissions", hasItems(
                        "system:dict:add", "system:dict:edit", "system:dict:remove")))
                .andExpect(jsonPath("$.data.menus[*].path", hasItems("/system/dict")));
    }

    @Test
    void blankSaveIsRejectedWithFieldMessages() throws Exception {
        mockMvc.perform(post("/admin/system/dict/type/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"\",\"dictType\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(containsString("字典名称不能为空")))
                .andExpect(jsonPath("$.message").value(containsString("字典类型不能为空")));

        mockMvc.perform(post("/admin/system/dict/data/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"\",\"dictLabel\":\"\",\"dictValue\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(containsString("字典类型不能为空")))
                .andExpect(jsonPath("$.message").value(containsString("字典标签不能为空")))
                .andExpect(jsonPath("$.message").value(containsString("字典值不能为空")));
    }

    private long saveType(String dictType, String dictName, int status, String remark)
            throws Exception {
        mockMvc.perform(post("/admin/system/dict/type/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"" + dictName + "\",\"dictType\":\"" + dictType
                                + "\",\"status\":" + status + ",\"remark\":\"" + remark + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_dict_type WHERE dict_type = ? AND is_deleted = 0",
                Long.class, dictType);
    }

    private long saveData(String dictType, String label, String value, int sort, int status,
                          String remark) throws Exception {
        mockMvc.perform(post("/admin/system/dict/data/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"" + dictType + "\",\"dictLabel\":\"" + label
                                + "\",\"dictValue\":\"" + value + "\",\"sortValue\":" + sort
                                + ",\"status\":" + status + ",\"remark\":\"" + remark + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_dict_data WHERE dict_type = ? AND dict_value = ? "
                        + "AND is_deleted = 0", Long.class, dictType, value);
    }

    private void insertPageOnlyPrincipal() {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, 'T251角色', 't251_role', 1, 0)", PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, is_deleted) "
                + "VALUES (?, 't251-page', '', 'T251用户', 1, 0)", PAGE_ONLY_USER_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                PAGE_ONLY_USER_ID, PAGE_ONLY_ROLE_ID);
        grantMenu(menuIdByPath("/system/dict"));
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
