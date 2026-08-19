package com.atguigu.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/auth-contract-schema.sql")
public class LoginAuthContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void correctUsernameAndPasswordReturnToken() throws Exception {
        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    public void protectedEndpointWithoutTokenReturns208() throws Exception {
        mockMvc.perform(get("/admin/system/index/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    public void loginTokenCanAccessCurrentUserInfo() throws Exception {
        String loginBody = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(loginBody, "$.data.token");

        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("管理员"))
                .andExpect(jsonPath("$.data.routers").exists());
    }

    @TestConfiguration
    static class InMemoryRedisConfig {
        @Bean
        @Primary
        public RedisTemplate<String, String> redisTemplate() {
            ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
            RedisTemplate<String, String> template = new RedisTemplate<String, String>() {
                @Override
                @SuppressWarnings("unchecked")
                public ValueOperations<String, String> opsForValue() {
                    return (ValueOperations<String, String>) Proxy.newProxyInstance(
                            ValueOperations.class.getClassLoader(),
                            new Class<?>[]{ValueOperations.class},
                            (proxy, method, args) -> {
                                if ("set".equals(method.getName()) && args != null && args.length >= 2) {
                                    store.put(String.valueOf(args[0]), String.valueOf(args[1]));
                                    return null;
                                }
                                if ("get".equals(method.getName()) && args != null && args.length >= 1) {
                                    return store.get(args[0]);
                                }
                                if ("toString".equals(method.getName())) {
                                    return "InMemoryValueOperations";
                                }
                                throw new UnsupportedOperationException(method.getName());
                            });
                }

                @Override
                public void afterPropertiesSet() {
                }
            };
            return template;
        }
    }
}
