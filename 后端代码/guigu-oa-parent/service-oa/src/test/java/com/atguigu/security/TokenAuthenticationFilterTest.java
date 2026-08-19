package com.atguigu.security;

import com.atguigu.security.filter.TokenAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TokenAuthenticationFilterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @AfterEach
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void missingTokenOnProtectedApiReturnsLoginError() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/admin/system/index/info", null, chain);

        assertLoginError(response);
        assertEquals(0, chain.calls);
    }

    @Test
    public void swaggerUiIsReachableWithoutToken() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/swagger-ui/index.html", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void swaggerUiHtmlIsReachableWithoutToken() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/swagger-ui.html", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void openApiDocsAreReachableWithoutToken() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/v3/api-docs", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void openApiDocsSubpathIsReachableWithoutToken() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/v3/api-docs/swagger-config", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    @Test
    public void loginEndpointSkipsTokenCheck() throws Exception {
        RecordingFilterChain chain = new RecordingFilterChain();
        MockHttpServletResponse response = filter("/admin/system/index/login", null, chain);

        assertEquals("", response.getContentAsString());
        assertEquals(1, chain.calls);
    }

    private MockHttpServletResponse filter(String uri, String token, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (token != null) {
            request.addHeader("token", token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TokenAuthenticationFilter(null).doFilter(request, response, chain);
        return response;
    }

    private void assertLoginError(MockHttpServletResponse response) throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertEquals(208, body.get("code").asInt());
        assertTrue(body.get("message").asString().contains("认证失败"));
    }

    private static final class RecordingFilterChain implements FilterChain {
        private int calls;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            calls++;
        }
    }
}
