package com.lautung.oa.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_sys_user_read;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
@Sql("/auth-contract-schema.sql")
class SysUserReadContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void userPageOmitsThePasswordHashForEveryRecord() throws Exception {
        String body = mockMvc.perform(get("/admin/system/sysUser/1/10").header("token", loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> usernames = JsonPath.read(body, "$.data.records[*].username");
        assertTrue(usernames.contains("admin"));
        assertEquals(List.of(), JsonPath.read(body, "$.data.records[*].password"));
        assertEquals(List.of(), JsonPath.read(body, "$.data.records[*].tokenVersion"));
    }

    @Test
    void userDetailOmitsThePasswordHash() throws Exception {
        String body = mockMvc.perform(get("/admin/system/sysUser/get/1").header("token", loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> user = JsonPath.read(body, "$.data");
        assertEquals("admin", user.get("username"));
        assertFalse(user.containsKey("password"));
        assertFalse(user.containsKey("tokenVersion"));
    }

    private String loginToken() throws Exception {
        String response = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }
}
