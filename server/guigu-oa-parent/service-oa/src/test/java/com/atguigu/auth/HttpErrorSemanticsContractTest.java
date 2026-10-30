package com.atguigu.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import com.atguigu.security.filter.TokenLoginFilterTest;
import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 容器级错误语义（#145 修复后的契约）：未知路径 404、方法不匹配 405，
 * 响应体仍是业务信封（code 201），不再被全局异常处理吞成 HTTP 200。
 * 认证失败的统一 401 用例在 {@link LoginAuthContractTest}。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_http_error_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/auth-contract-schema.sql")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
public class HttpErrorSemanticsContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void unknownPathReturnsReal404WithTheBusinessEnvelope() throws Exception {
        mockMvc.perform(get("/admin/no-such-endpoint").header("token", adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("请求的资源不存在"));
    }

    @Test
    public void methodMismatchOnAPostOnlyEndpointReturnsReal405() throws Exception {
        // doAssign 是 POST-only 且没有同段数的 GET 路由模式，命中方法不匹配而非参数解析。
        mockMvc.perform(get("/admin/system/sysRole/doAssign").header("token", adminToken()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("请求方法不支持"));
    }

    @Test
    public void methodMismatchOnAThreeSegmentPutEndpointReturnsReal405() throws Exception {
        mockMvc.perform(get("/admin/system/sysUser/updateStatus/1/1").header("token", adminToken()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("请求方法不支持"));
    }

    @Test
    public void aTypeMismatchOnThePagingRouteStaysABusinessEnvelope() throws Exception {
        // GET publish/1 命中 GET {page}/{limit} 的分页路由："publish" 转 Long 失败
        // 是业务参数错误，不在 #145 的容器级范围，契约保持 HTTP 200 + code 201。
        // 类型不匹配的专门文案还没做（ADR 0033），所以这里钉的是兜底消息——它同时也是「没被当成 4xx」的证据。
        String body = mockMvc.perform(get("/admin/process/processTemplate/publish/1")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("执行全局异常处理..."))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(201, JsonPath.<Integer>read(body, "$.code").intValue());
    }

    private String adminToken() throws Exception {
        String response = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }
}
