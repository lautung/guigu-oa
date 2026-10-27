package com.atguigu.auth;

import com.atguigu.auth.controller.IndexController;
import com.atguigu.auth.controller.SysMenuController;
import com.atguigu.auth.controller.SysRoleController;
import com.atguigu.auth.controller.SysUserController;
import com.atguigu.common.jwt.JwtHelper;
import com.atguigu.process.controller.OaProcessController;
import com.atguigu.process.controller.OaProcessTemplateController;
import com.atguigu.process.controller.OaProcessTypeController;
import com.atguigu.process.controller.api.ProcessController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_config_route_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class ConfigurationRouteAuthorizationContractTest {

    private static final Map<String, String> EXPECTED_GATES = new LinkedHashMap<>();
    private static final Set<Class<?>> CONFIGURATION_CONTROLLERS = Set.of(
            SysUserController.class,
            SysMenuController.class,
            SysRoleController.class,
            OaProcessTypeController.class,
            OaProcessTemplateController.class,
            OaProcessController.class);

    static {
        expect(SysUserController.class, "sysUser",
                "updateStatus", "index", "get", "save", "updateById", "remove");
        expect(SysMenuController.class, "sysRole", "toAssign", "doAssign");
        expect(SysMenuController.class, "sysMenu",
                "findNodes", "save", "updateById", "remove");
        expect(SysRoleController.class, "sysUser", "toAssign", "doAssign");
        expect(SysRoleController.class, "sysRole",
                "findAll", "pageQueryRole", "save", "get", "update", "remove", "batchRemove");
        expect(OaProcessTypeController.class, "processType",
                "findAll", "index", "get", "save", "updateById", "remove");
        expect(OaProcessTemplateController.class, "processTemplate",
                "index", "get", "save", "updateById", "remove", "uploadProcessDefinition", "publish");
        expect(OaProcessController.class, "process", "index");
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @AfterEach
    void cleanUpGrantedUsers() {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (101, 102)");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id IN (101, 102)");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (101, 102)");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id IN (101, 102)");
    }

    @Test
    void everyConfigurationRouteCarriesTheGateForItsMenu() {
        Map<String, String> actual = new LinkedHashMap<>();
        for (HandlerMethod handler : handlerMapping.getHandlerMethods().values()) {
            if (!CONFIGURATION_CONTROLLERS.contains(handler.getBeanType())) {
                continue;
            }
            String key = keyOf(handler);
            assertTrue(EXPECTED_GATES.containsKey(key),
                    "new configuration route must be added to the contract: " + key);
            PreAuthorize gate = AnnotatedElementUtils.findMergedAnnotation(
                    handler.getMethod(), PreAuthorize.class);
            assertNotNull(gate, "missing configuration gate on " + key);
            actual.put(key, gate.value());
        }

        assertEquals(EXPECTED_GATES, actual);
    }

    @Test
    void noWorkOrRecognitionRouteCarriesAConfigurationGate() {
        boolean sawWorkController = false;
        boolean sawRecognitionController = false;
        for (HandlerMethod handler : handlerMapping.getHandlerMethods().values()) {
            if (handler.getBeanType().equals(ProcessController.class)) {
                sawWorkController = true;
            }
            if (handler.getBeanType().equals(IndexController.class)) {
                sawRecognitionController = true;
            }
            PreAuthorize gate = AnnotatedElementUtils.findMergedAnnotation(
                    handler.getMethod(), PreAuthorize.class);
            if (gate != null && gate.value().contains("configurationMenuGate")) {
                assertTrue(EXPECTED_GATES.containsKey(keyOf(handler)),
                        "unexpected configuration gate on " + keyOf(handler));
            }
        }
        assertTrue(sawWorkController);
        assertTrue(sawRecognitionController);
    }

    @Test
    void seedAdminReachesEveryConfigurationPageWhileRolelessUserIsDenied() throws Exception {
        long templateId = templateId();
        assertCode("/admin/system/sysUser/1/10", 1L, "admin", 200);
        assertCode("/admin/system/sysRole/1/10", 1L, "admin", 200);
        assertCode("/admin/system/sysMenu/findNodes", 1L, "admin", 200);
        assertCode("/admin/process/processType/1/10", 1L, "admin", 200);
        assertCode("/admin/process/processTemplate/1/10", 1L, "admin", 200);
        assertCode("/admin/process/processTemplate/get/" + templateId, 1L, "admin", 200);
        assertCode("/admin/process/1/10", 1L, "admin", 200);

        assertCode("/admin/system/sysUser/1/10", 2L, "user", 205);
        assertCode("/admin/system/sysRole/1/10", 2L, "user", 205);
        assertCode("/admin/system/sysMenu/findNodes", 2L, "user", 205);
        assertCode("/admin/process/processType/1/10", 2L, "user", 205);
        assertCode("/admin/process/processTemplate/1/10", 2L, "user", 205);
        assertCode("/admin/process/processTemplate/get/" + templateId, 2L, "user", 205);
        assertCode("/admin/process/1/10", 2L, "user", 205);
    }

    @Test
    void aSinglePageGrantOpensThatPageOnly() throws Exception {
        grant(101L, 101L, "route-user", "sysUser");

        assertCode("/admin/system/sysUser/1/10", 101L, "route-user", 200);
        assertCode("/admin/system/sysRole/1/10", 101L, "route-user", 205);
        assertCode("/admin/system/sysMenu/findNodes", 101L, "route-user", 205);
        assertCode("/admin/process/1/10", 101L, "route-user", 205);

        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", jwtHelper.createAccessToken(101L, "route-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configMenus[*].path", hasItems("/system", "/system/sysUser")))
                .andExpect(jsonPath("$.data.configMenus[*].path", not(hasItem("/system/sysRole"))));
    }

    @Test
    void hiddenTemplatePageUsesItsVisibleParentMenu() throws Exception {
        grant(102L, 102L, "route-template", "processTemplate");

        assertCode("/admin/process/processTemplate/get/" + templateId(), 102L, "route-template", 200);
        assertCode("/admin/process/processTemplate/1/10", 102L, "route-template", 200);
        assertCode("/admin/process/processType/1/10", 102L, "route-template", 205);

        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", jwtHelper.createAccessToken(102L, "route-template")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configMenus[*].path", hasItems(
                        "/processSet", "/processSet/processTemplate", "/processSet/templateSet")))
                .andExpect(jsonPath("$.data.configMenus[?(@.path == '/processSet/templateSet')].parentPath",
                        hasItem("/processSet/processTemplate")))
                .andExpect(jsonPath("$.data.configMenus[?(@.path == '/processSet/templateSet')].hidden",
                        hasItem(true)));
    }

    @Test
    void workAndRecognitionRoutesStayAvailableWithoutConfigurationGrant() throws Exception {
        assertCode("/admin/process/findPending/1/10", 2L, "user", 200);
        assertCode("/admin/system/index/info", 2L, "user", 200);
    }

    private void assertCode(String path, long userId, String username, int code) throws Exception {
        mockMvc.perform(get(path).header("token", jwtHelper.createAccessToken(userId, username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void grant(long roleId, long userId, String username, String menuPath) {
        Integer menuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = ?", Integer.class, menuPath);
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, name, status, is_deleted) "
                        + "VALUES (?, ?, '', ?, 1, 0)", userId, username, username);
        jdbcTemplate.update(
                "INSERT INTO sys_role (id, role_name, role_code, is_deleted) VALUES (?, ?, ?, 0)",
                roleId, "route-" + roleId, "route_" + roleId);
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (?, ?, ?, 0)",
                roleId + 100, roleId, menuId);
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (id, role_id, user_id, is_deleted) VALUES (?, ?, ?, 0)",
                userId + 100, roleId, userId);
    }

    private long templateId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM oa_process_template WHERE name = '请假申请'", Long.class);
    }

    private static String keyOf(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
    }

    private static void expect(Class<?> controller, String menuPath, String... methods) {
        for (String method : methods) {
            EXPECTED_GATES.put(
                    controller.getSimpleName() + "#" + method,
                    "@configurationMenuGate.isGranted('" + menuPath + "')");
        }
    }
}
