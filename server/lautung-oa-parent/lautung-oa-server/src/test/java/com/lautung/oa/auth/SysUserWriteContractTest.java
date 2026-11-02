package com.lautung.oa.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户名唯一的 HTTP 契约（ADR 0042）：重名 save/update 得到 201 命名拒绝；
 * 删除让位后同名可重建、被删行 username 置空；查重判定在配置 gate 之后（未授予 205）。
 * 正向路径依赖 Flyway 种子的授予（testing.md：auth-contract-schema.sql 的 gate 恒拒绝场景不适用于这里）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_sys_user_write;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SysUserWriteContractTest {

    private static final String PLAIN_PASSWORD = "111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsASaveWithAnExistingUsernameWithTheNamedMessage() throws Exception {
        String adminToken = adminToken();
        saveUser(adminToken, "write_dup_a");

        mockMvc.perform(post("/admin/system/sysUser/save").header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"write_dup_a\",\"name\":\"重名\",\"password\":\"" + PLAIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void rejectsAnUpdateRenamingIntoAnotherUserAndAcceptsRenamingToTheOwnName() throws Exception {
        String adminToken = adminToken();
        saveUser(adminToken, "write_upd_a");
        long idB = saveUser(adminToken, "write_upd_b");

        // 改名撞上 write_upd_a：与新增同一种硬危害，同一命名拒绝。
        mockMvc.perform(put("/admin/system/sysUser/update").header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + idB + ",\"username\":\"write_upd_a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        // 改回自身现名：排除自身后无冲突，放行。
        mockMvc.perform(put("/admin/system/sysUser/update").header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + idB + ",\"username\":\"write_upd_b\",\"name\":\"自名\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void freesTheUsernameOnRemoveSoTheSameNameCanBeRecreated() throws Exception {
        String adminToken = adminToken();
        long id = saveUser(adminToken, "write_reuse");

        mockMvc.perform(delete("/admin/system/sysUser/remove/" + id).header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 让位发生在软删的同时：被删行 username 置空，不占唯一索引。
        String freed = jdbcTemplate.queryForObject(
                "SELECT username FROM sys_user WHERE id = ?", String.class, id);
        assertNull(freed, "软删后该行 username 应已置空");

        saveUser(adminToken, "write_reuse");
    }

    @Test
    void refusesASaveFromAUserWithoutTheConfigurationGrantBeforeTheUniquenessCheck() throws Exception {
        // 顺序契约（ADR 0034 的延伸）：查重判定在配置 gate 之后——未授予 + 重名 = 205，不是 201。
        mockMvc.perform(post("/admin/system/sysUser/save").header("token", plainUserToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"name\":\"冒名\",\"password\":\"" + PLAIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
    }

    /** 建号并返回新 id；重名已被前置断言覆盖，这里只服务正例路径。 */
    private long saveUser(String token, String username) throws Exception {
        mockMvc.perform(post("/admin/system/sysUser/save").header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"name\":\"" + username + "\",\"password\":\""
                                + PLAIN_PASSWORD + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        String page = mockMvc.perform(get("/admin/system/sysUser/1/50?keyword=" + username)
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(page, "$.data.records[0].id")).longValue();
    }

    private String adminToken() throws Exception {
        return loginToken("admin");
    }

    private String plainUserToken() throws Exception {
        return loginToken("user");
    }

    private String loginToken(String username) throws Exception {
        String response = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PLAIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }
}
