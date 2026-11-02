package com.lautung.oa.auth;

import com.lautung.oa.framework.config.springdoc.SpringDocConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class OpenApiContractTest {

    @Test
    public void knife4jDocketConfigMustStayAbsent() {
        assertNull(load("com.lautung.oa.framework.config.knife4j.Knife4jConfig"));
    }

    @Test
    public void springdocConfigExposesTokenHeader() {
        OpenAPI api = new SpringDocConfig().openAPI();
        SecurityScheme token = api.getComponents().getSecuritySchemes().get("token");
        assertNotNull(token);
        assertEquals(SecurityScheme.Type.APIKEY, token.getType());
        assertEquals(SecurityScheme.In.HEADER, token.getIn());
        assertEquals("token", token.getName());
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
