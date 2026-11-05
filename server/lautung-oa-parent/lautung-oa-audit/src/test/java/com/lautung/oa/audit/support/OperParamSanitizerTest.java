package com.lautung.oa.audit.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperParamSanitizerTest {

    private final OperParamSanitizer sanitizer = new OperParamSanitizer(new ObjectMapper());

    @Test
    void masksBlacklistedPasswordField() {
        String json = sanitizer.sanitize(new Object[]{new SaveRequest("alice", "111111")});

        assertTrue(json.contains("\"password\":\"***\""), json);
        assertFalse(json.contains("111111"), json);
        assertTrue(json.contains("alice"), json);
    }

    @Test
    void blacklistIsCaseInsensitiveAndCoversEveryDeclaredField() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("PASSWORD", "p1");
        payload.put("oldPassword", "p2");
        payload.put("NewPassword", "p3");
        payload.put("CONFIRMPASSWORD", "p4");
        payload.put("TOKEN", "t1");
        payload.put("refreshToken", "t2");
        payload.put("Secret", "s1");
        payload.put("AUTHORIZATION", "Bearer abc");
        payload.put("username", "alice");

        String json = sanitizer.sanitize(new Object[]{payload});

        for (String secret : List.of("p1", "p2", "p3", "p4", "t1", "t2", "s1", "Bearer abc")) {
            assertFalse(json.contains(secret), secret + " should be masked: " + json);
        }
        assertEquals(8, count(json, "\"***\""), json);
        assertTrue(json.contains("\"username\":\"alice\""), json);
    }

    @Test
    void masksBlacklistedFieldsInsideNestedObjectsAndArrays() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user", Map.of("name", "alice", "password", "nested-secret"));
        payload.put("items", List.of(Map.of("token", "array-secret"), Map.of("name", "keep")));

        String json = sanitizer.sanitize(new Object[]{payload});

        assertFalse(json.contains("nested-secret"), json);
        assertFalse(json.contains("array-secret"), json);
        assertTrue(json.contains("\"password\":\"***\""), json);
        assertTrue(json.contains("\"token\":\"***\""), json);
        assertTrue(json.contains("\"name\":\"keep\""), json);
    }

    @Test
    void masksPhoneNumbersAcrossTheWholeJsonString() {
        String json = sanitizer.sanitize(new Object[]{
                Map.of("phone", "13800131234", "name", "联系 13900139000")
        });

        assertTrue(json.contains("138****1234"), json);
        assertTrue(json.contains("139****9000"), json);
        assertFalse(json.contains("13800131234"), json);
    }

    @Test
    void truncatesOversizedParametersWithAMarker() {
        String json = sanitizer.sanitize(new Object[]{Map.of("payload", "x".repeat(5000))});

        assertEquals(OperParamSanitizer.OPER_PARAM_LIMIT, json.length());
        assertTrue(json.endsWith(AuditText.TRUNCATION_MARKER), json);
    }

    @Test
    void skipsTransportObjectsAndKeepsTheRequestBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/system/sysUser/save");

        String json = sanitizer.sanitize(new Object[]{request, Map.of("name", "alice")});

        assertEquals("[{\"name\":\"alice\"}]", json);
    }

    @Test
    void masksBareSensitiveParametersByName() {
        // 用户导入把初始密码作为独立 String 参数传入：字段黑名单管不到裸标量，必须按参数名打码。
        String json = sanitizer.sanitize(
                new Object[]{"users.xlsx", "import-secret"},
                new String[]{"file", "initialPassword"});

        assertFalse(json.contains("import-secret"), json);
        assertTrue(json.contains("\"***\""), json);
        assertTrue(json.contains("users.xlsx"), json);
    }

    @Test
    void keepsBareParametersWhenNamesAreUnavailableOrNotSensitive() {
        assertEquals("[\"alice\"]", sanitizer.sanitize(new Object[]{"alice"}));
        assertEquals("[\"alice\",\"import-secret\"]",
                sanitizer.sanitize(new Object[]{"alice", "import-secret"}, new String[]{"name", "note"}));
    }

    private static int count(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    static class SaveRequest {

        private final String username;

        private final String password;

        SaveRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }
}
