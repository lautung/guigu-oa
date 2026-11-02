package com.lautung.oa.audit;

import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 操作日志的 HTTP 合同（ADR 0054）：一条真实后台写请求恰好产生一行，字段齐全、参数已打码、
 * {@code create_time} 已填；被 gate 挡下的 205 与被 {@code @Valid} 拦下的 201 都不产生行。
 *
 * <p>用真实 Flyway 迁移（含 V112）与种子授予，因此也钉住了「审计行在响应返回前已同步落库」。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_oper_log_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class OperLogContractTest {

    private static final String PLAIN_PASSWORD = "111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @BeforeEach
    void resetFixtures() {
        clearFixtures();
    }

    @AfterEach
    void clearFixtures() {
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        jdbcTemplate.update("UPDATE sys_user SET dept_id = NULL WHERE id = 1");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id = 77");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'oper_log_%'");
    }

    @Test
    void recordsExactlyOneRowForARealBackofficeWrite() throws Exception {
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (77, 0, ',0,77,', '审计部', 1, 0)");
        jdbcTemplate.update("UPDATE sys_user SET dept_id = 77 WHERE id = 1");

        mockMvc.perform(post("/admin/system/sysUser/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"oper_log_target\",\"name\":\"审计目标\","
                                + "\"password\":\"" + PLAIN_PASSWORD + "\",\"phone\":\"13800131234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> row = onlyRow();
        assertEquals("用户管理·新增用户", row.get("title"));
        assertEquals(1, intValue(row, "business_type"));
        assertEquals("com.lautung.oa.auth.controller.SysUserController#save", row.get("method"));
        assertEquals("POST", row.get("request_method"));
        assertEquals(1L, longValue(row, "oper_id"));
        assertEquals("管理员", row.get("oper_name"));
        assertEquals(77L, longValue(row, "dept_id"));
        assertEquals("审计部", row.get("dept_name"));
        assertEquals("/admin/system/sysUser/save", row.get("oper_url"));
        assertNotNull(row.get("oper_ip"), "oper_ip should be recorded");

        String param = (String) row.get("oper_param");
        assertTrue(param.contains("\"password\":\"***\""), param);
        assertFalse(param.contains(PLAIN_PASSWORD), param);
        assertTrue(param.contains("138****1234"), param);
        assertFalse(param.contains("13800131234"), param);
        assertTrue(param.contains("oper_log_target"), param);

        assertEquals(200, intValue(row, "result_code"));
        assertEquals("成功", row.get("result_message"));
        assertEquals(0, intValue(row, "status"));
        assertNull(row.get("error_msg"));
        assertTrue(longValue(row, "cost_time") >= 0);
        assertNotNull(row.get("create_time"), "create_time must be filled by the common-field handler");
    }

    @Test
    void gateAndValidationRejectionsNeverProduceARow() throws Exception {
        // 已认证但未被授予：校验先于 gate（ADR 0034），所以用良构体才轮到 205。
        mockMvc.perform(post("/admin/system/sysUser/save")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"oper_log_gate\",\"name\":\"越权\",\"password\":\"" + PLAIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        assertEquals(0, rowCount(), "a 205 rejection must not leave an audit row");

        // @Valid 在参数解析期失败，方法体从未进入。
        mockMvc.perform(post("/admin/system/sysUser/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("姓名不能为空；密码不能为空；用户名不能为空"));
        assertEquals(0, rowCount(), "a validation failure must not leave an audit row");
    }

    @Test
    void aNon200EnvelopeIsRecordedAsAFailureWithTheAttemptParameter() throws Exception {
        // 不存在的角色 id：控制器自己返回 Result.fail()（非 200 信封，不是抛异常）。
        mockMvc.perform(delete("/admin/system/sysRole/remove/999999").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("失败"));

        Map<String, Object> failure = failureRow();
        assertEquals(201, intValue(failure, "result_code"));
        assertEquals("失败", failure.get("result_message"));
        assertEquals(1, intValue(failure, "status"));
        assertNull(failure.get("error_msg"));
        assertEquals("[999999]", failure.get("oper_param"));
    }

    @Test
    void aThrownBusinessExceptionIsRecordedAsAFailureAndTheResponseStillCarriesTheBusinessMessage()
            throws Exception {
        String body = "{\"username\":\"oper_log_dup\",\"name\":\"重名\",\"password\":\"" + PLAIN_PASSWORD + "\"}";
        mockMvc.perform(post("/admin/system/sysUser/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/sysUser/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        assertEquals(2, rowCount());
        Map<String, Object> failure = failureRow();
        assertEquals(1, intValue(failure, "status"));
        assertNull(failure.get("result_code"));
        assertTrue(((String) failure.get("error_msg")).contains("用户名已存在"),
                String.valueOf(failure.get("error_msg")));
        String param = (String) failure.get("oper_param");
        assertTrue(param.contains("\"password\":\"***\""), param);
        assertFalse(param.contains(PLAIN_PASSWORD), param);
    }

    private String adminToken() {
        return jwtHelper.createTokenPair(1L, "admin", 0L).token();
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_oper_log", Integer.class);
    }

    private Map<String, Object> failureRow() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM sys_oper_log WHERE status = 1");
        assertEquals(1, rows.size(), "expected exactly one failure row");
        return lowerCaseKeys(rows.get(0));
    }

    private Map<String, Object> onlyRow() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM sys_oper_log");
        assertEquals(1, rows.size(), "exactly one backoffice write should produce exactly one row");
        return lowerCaseKeys(rows.get(0));
    }

    private static Map<String, Object> lowerCaseKeys(Map<String, Object> row) {
        Map<String, Object> normalized = new HashMap<>();
        row.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }

    private static int intValue(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    private static long longValue(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }
}
