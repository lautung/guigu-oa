package com.lautung.oa.auth;

import com.lautung.oa.audit.controller.SysLoginLogController;
import com.lautung.oa.audit.controller.SysOperLogController;
import com.lautung.oa.auth.controller.IndexController;
import com.lautung.oa.auth.controller.SysMenuController;
import com.lautung.oa.auth.controller.SysRoleController;
import com.lautung.oa.auth.controller.SysUserController;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.notice.controller.NoticeAdminController;
import com.lautung.oa.process.controller.OaProcessController;
import com.lautung.oa.process.controller.OaProcessTemplateController;
import com.lautung.oa.process.controller.OaProcessTypeController;
import com.lautung.oa.process.controller.api.ProcessController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_backoffice_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class BackofficeRouteAuthorizationContractTest {

    private static final Pattern GATE = Pattern.compile(
            "@backofficeGate\\.isAllowed\\('([^']+)'(?:,'([^']+)')?\\)");
    private static final Map<String, String> EXPECTED_GATES = new LinkedHashMap<>();
    private static final Set<Class<?>> BACKOFFICE_CONTROLLERS = Set.of(
            SysUserController.class,
            SysMenuController.class,
            SysRoleController.class,
            OaProcessTypeController.class,
            OaProcessTemplateController.class,
            OaProcessController.class,
            SysOperLogController.class,
            SysLoginLogController.class,
            NoticeAdminController.class);

    static {
        expect(SysUserController.class, "/system/sysUser", null,
                "updateStatus", "index", "get", "save", "updateById", "remove");
        expect(SysUserController.class, "/system/sysUser", "system:user:edit", "updateStatus");
        expect(SysUserController.class, "/system/sysUser", "system:user:add", "save");
        expect(SysUserController.class, "/system/sysUser", "system:user:edit", "updateById");
        expect(SysUserController.class, "/system/sysUser", "system:user:remove", "remove");
        expect(SysMenuController.class, "/system/sysRole", null, "toAssign");
        expect(SysMenuController.class, "/system/sysRole", "system:role:assignAuth", "doAssign");
        expect(SysMenuController.class, "/system/sysMenu", null, "findNodes");
        expect(SysMenuController.class, "/system/sysMenu", "system:menu:add", "save");
        expect(SysMenuController.class, "/system/sysMenu", "system:menu:edit", "updateById");
        expect(SysMenuController.class, "/system/sysMenu", "system:menu:remove", "remove");
        expect(SysRoleController.class, "/system/sysUser", null, "toAssign");
        expect(SysRoleController.class, "/system/sysUser", "system:user:assignRole", "doAssign");
        expect(SysRoleController.class, "/system/sysRole", null,
                "findAll", "pageQueryRole", "get", "depts");
        expect(SysRoleController.class, "/system/sysRole", "system:role:add", "save");
        expect(SysRoleController.class, "/system/sysRole", "system:role:edit", "update");
        expect(SysRoleController.class, "/system/sysRole", "system:role:remove", "remove", "batchRemove");
        expect(OaProcessTypeController.class, "/system/processSet/processType", null,
                "findAll", "index", "get");
        expect(OaProcessTypeController.class, "/system/processSet/processType", "process:type:add", "save");
        expect(OaProcessTypeController.class, "/system/processSet/processType", "process:type:edit", "updateById");
        expect(OaProcessTypeController.class, "/system/processSet/processType", "process:type:remove", "remove");
        expect(OaProcessTemplateController.class, "/system/processSet/processTemplate", null, "index", "get");
        expect(OaProcessTemplateController.class, "/system/processSet/processTemplate", "process:template:add", "save");
        expect(OaProcessTemplateController.class, "/system/processSet/processTemplate", "process:template:edit", "updateById");
        expect(OaProcessTemplateController.class, "/system/processSet/processTemplate", "process:template:remove", "remove");
        expect(OaProcessTemplateController.class, "/system/processSet/processTemplate", "process:template:upload", "uploadProcessDefinition");
        expect(OaProcessTemplateController.class, "/system/processSet/processTemplate", "process:template:publish", "publish");
        expect(OaProcessController.class, "/system/processMgr/process", null, "index");
        // 操作日志（#214）：读走页面授予，删除/清空各要自己的按钮点。
        expect(SysOperLogController.class, "/system/log/operLog", null, "index", "get");
        expect(SysOperLogController.class, "/system/log/operLog", "system:operLog:remove", "remove");
        expect(SysOperLogController.class, "/system/log/operLog", "system:operLog:clean", "clean");
        // 登录日志（#216）：读走页面授予，删除/清空/解锁各要自己的按钮点。
        expect(SysLoginLogController.class, "/system/log/loginLog", null, "index");
        expect(SysLoginLogController.class, "/system/log/loginLog", "system:loginLog:remove", "remove");
        expect(SysLoginLogController.class, "/system/log/loginLog", "system:loginLog:clean", "clean");
        expect(SysLoginLogController.class, "/system/log/loginLog", "system:loginLog:unlock", "unlock");
        // 通知公告（#218）：读（列表/详情）只走页面授予；写各要按钮点，没有 :query 读权限。
        expect(NoticeAdminController.class, "/system/notice", null, "page", "detail");
        expect(NoticeAdminController.class, "/system/notice", "system:notice:add", "create");
        // 上传是新增与编辑共用的编辑器：add 或 edit 任一按钮点都可以（ADR 0055 修订）。
        expectEither(NoticeAdminController.class, "/system/notice",
                "system:notice:add", "system:notice:edit", "upload");
        expect(NoticeAdminController.class, "/system/notice", "system:notice:edit", "update");
        expect(NoticeAdminController.class, "/system/notice", "system:notice:remove", "delete");
        expect(NoticeAdminController.class, "/system/notice", "system:notice:publish", "publish", "withdraw");
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
        jdbcTemplate.update("UPDATE sys_user SET status = 1 WHERE id IN (2, 101)");
    }

    @Test
    void everyBackofficeRouteCarriesTheGateForItsMenu() {
        Map<String, String> actual = new LinkedHashMap<>();
        for (HandlerMethod handler : handlerMapping.getHandlerMethods().values()) {
            if (!BACKOFFICE_CONTROLLERS.contains(handler.getBeanType())) {
                continue;
            }
            String key = keyOf(handler);
            assertTrue(EXPECTED_GATES.containsKey(key),
                    "new backoffice route must be added to the contract: " + key);
            PreAuthorize gate = AnnotatedElementUtils.findMergedAnnotation(
                    handler.getMethod(), PreAuthorize.class);
            assertNotNull(gate, "missing backoffice gate on " + key);
            actual.put(key, gate.value());
        }

        assertEquals(EXPECTED_GATES, actual);
    }

    @Test
    void everyControllerInTheBackofficePackagesIsInTheGateContract() {
        for (HandlerMethod handler : handlerMapping.getHandlerMethods().values()) {
            Class<?> bean = handler.getBeanType();
            if (bean.equals(IndexController.class) || bean.equals(ProcessController.class)) {
                continue;
            }
            boolean backofficeController = bean.getPackageName().equals("com.lautung.oa.auth.controller")
                    || bean.getSimpleName().startsWith("Oa");
            if (backofficeController) {
                assertTrue(EXPECTED_GATES.containsKey(keyOf(handler)),
                        "new backoffice controller route must be added to the contract: " + keyOf(handler));
            }
        }
    }

    @Test
    void everyBackofficeWriteRouteDeclaresAPageAndButtonPoint() {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!BACKOFFICE_CONTROLLERS.contains(handler.getBeanType())) {
                continue;
            }
            String key = keyOf(handler);
            Gate gate = gateOf(handler);
            assertNotNull(gate, "missing backoffice gate on " + key);

            if (isWrite(entry.getKey())) {
                assertFalse(gate.permissions().isEmpty(),
                        "backoffice write route must declare a button point: " + key);
            } else {
                assertTrue(gate.permissions().isEmpty(),
                        "backoffice read route must pass only the page: " + key);
            }

            Integer pageId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_menu WHERE path = ? AND type = 1 AND status = 1 AND is_deleted = 0",
                    Integer.class,
                    gate.page());
            assertNotNull(pageId, key + " must target a granted page row: " + gate.page());
            assertEquals(0, count("SELECT COUNT(*) FROM sys_menu p WHERE p.id = "
                            + "(SELECT parent_id FROM sys_menu WHERE id = " + pageId + ") AND p.type = 1"),
                    key + " must target a visible page, not a hidden page: " + gate.page());

            for (String permission : gate.permissions()) {
                Integer buttonParent = jdbcTemplate.queryForObject(
                        "SELECT parent_id FROM sys_menu WHERE perms = ? AND type = 2 AND is_deleted = 0",
                        Integer.class,
                        permission);
                assertEquals(pageId, buttonParent,
                        key + " button " + permission + " must hang under " + gate.page());
            }
        }
    }

    @Test
    void statusToggleAndPublishAreWrittenOnlyByWriteMethods() throws Exception {
        assertOnlyMethod(SysUserController.class, "updateStatus", RequestMethod.PUT);
        assertOnlyMethod(OaProcessTemplateController.class, "publish", RequestMethod.POST);

        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        Integer statusBefore = userStatus(1L);

        // 旧 GET 路径没有处理器：DispatcherServlet 抛方法不支持，由 GlobalExceptionHandler 收成失败信封。
        mockMvc.perform(get("/admin/system/sysUser/updateStatus/1/0").header("token", token))
                .andExpect(this::rejectsTheHttpMethod);
        assertEquals(statusBefore, userStatus(1L));
    }

    @Test
    void noWorkOrRecognitionRouteCarriesABackofficeGate() {
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
            if (gate != null && gate.value().contains("backofficeGate")) {
                assertTrue(EXPECTED_GATES.containsKey(keyOf(handler)),
                        "unexpected backoffice gate on " + keyOf(handler));
            }
        }
        assertTrue(sawWorkController);
        assertTrue(sawRecognitionController);
    }

    @Test
    void seedAdminReachesEveryBackofficePageWhileRolelessUserIsDenied() throws Exception {
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
    void adminInfoCarriesRolesPermissionsAndMenusWithoutLegacyFields() throws Exception {
        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.roles", hasItem("super_admin")))
                .andExpect(jsonPath("$.data.permissions", hasItems(
                        "system:user:add", "system:user:edit", "system:user:remove",
                        "system:user:assignRole", "system:role:add", "system:role:edit",
                        "system:role:remove", "system:role:assignAuth",
                        "system:menu:add", "system:menu:edit", "system:menu:remove",
                        "process:type:add", "process:type:edit", "process:type:remove",
                        "process:template:add", "process:template:edit", "process:template:remove",
                        "process:template:upload", "process:template:publish",
                        "system:operLog:remove", "system:operLog:clean",
                        "system:loginLog:remove", "system:loginLog:clean", "system:loginLog:unlock")))
                .andExpect(jsonPath("$.data.menus[*].path", hasItems(
                        "/system",
                        "/system/sysUser",
                        "/system/sysRole",
                        "/system/assignAuth",
                        "/system/processSet",
                        "/system/processSet/processType",
                        "/system/processSet/processTemplate",
                        "/system/processSet/templateSet",
                        "/system/processMgr",
                        "/system/processMgr/process",
                        "/system/log",
                        "/system/log/operLog",
                        "/system/log/loginLog")))
                .andExpect(jsonPath("$.data.menus[*].type", not(hasItem(2))))
                .andExpect(jsonPath("$.data.menus[?(@.path == '/system/sysUser')].parentPath",
                        hasItem("/system")))
                .andExpect(jsonPath("$.data.menus[?(@.path == '/system/sysUser')].icon").exists())
                .andExpect(jsonPath("$.data.menus[?(@.path == '/system/assignAuth')].hidden",
                        hasItem(true)))
                .andExpect(jsonPath("$.data.configMenus").doesNotExist())
                .andExpect(jsonPath("$.data.buttons").doesNotExist());
    }

    @Test
    void directoryOnlyGrantDeniesEveryBackofficeRead() throws Exception {
        grant(101L, 101L, "route-directory", "/system");

        assertCode("/admin/system/sysUser/1/10", 101L, "route-directory", 205);
        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", jwtHelper.createTokenPair(101L, "route-directory", 0L).token()))
                .andExpect(jsonPath("$.data.menus").isEmpty())
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    @Test
    void buttonOnlyGrantDoesNotOpenItsPage() throws Exception {
        grantPermission(101L, 101L, "route-button-only", "system:user:add");

        assertCode("/admin/system/sysUser/1/10", 101L, "route-button-only", 205);
        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", jwtHelper.createTokenPair(101L, "route-button-only", 0L).token()))
                .andExpect(jsonPath("$.data.menus").isEmpty())
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    @Test
    void hiddenPageGrantedAloneStaysIneffective() throws Exception {
        grant(101L, 101L, "route-hidden-only", "/system/processSet/templateSet");

        assertCode("/admin/process/processTemplate/get/" + templateId(), 101L,
                "route-hidden-only", 205);
    }

    @Test
    void aPageGrantOpensReadsOnlyAndItsButtonOpensTheWrite() throws Exception {
        grant(101L, 101L, "route-page", "/system/sysUser");

        assertCode("/admin/system/sysUser/1/10", 101L, "route-page", 200);
        mockMvc.perform(put("/admin/system/sysUser/updateStatus/101/0")
                        .header("token", jwtCreatorToken(101L, "route-page")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        grantPermission(101L, 101L, "route-page", "system:user:edit");

        mockMvc.perform(put("/admin/system/sysUser/updateStatus/101/0")
                        .header("token", jwtCreatorToken(101L, "route-page")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, userStatus(101L));
    }

    @Test
    void aSinglePageGrantOpensThatPageOnly() throws Exception {
        grant(101L, 101L, "route-user", "/system/sysUser");

        assertCode("/admin/system/sysUser/1/10", 101L, "route-user", 200);
        assertCode("/admin/system/sysRole/1/10", 101L, "route-user", 205);
        assertCode("/admin/system/sysMenu/findNodes", 101L, "route-user", 205);
        assertCode("/admin/process/1/10", 101L, "route-user", 205);

        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", jwtHelper.createTokenPair(101L, "route-user", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menus[*].path", hasItems("/system", "/system/sysUser")))
                .andExpect(jsonPath("$.data.menus[*].path", not(hasItem("/system/sysRole"))));
    }

    @Test
    void hiddenTemplatePageUsesItsVisibleParentMenu() throws Exception {
        grant(102L, 102L, "route-template", "/system/processSet/processTemplate");

        assertCode("/admin/process/processTemplate/get/" + templateId(), 102L, "route-template", 200);
        assertCode("/admin/process/processTemplate/1/10", 102L, "route-template", 200);
        assertCode("/admin/process/processType/1/10", 102L, "route-template", 205);

        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", jwtHelper.createTokenPair(102L, "route-template", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menus[*].path", hasItems(
                        "/system", "/system/processSet",
                        "/system/processSet/processTemplate", "/system/processSet/templateSet")))
                .andExpect(jsonPath("$.data.menus[?(@.path == '/system/processSet/templateSet')].parentPath",
                        hasItem("/system/processSet/processTemplate")))
                .andExpect(jsonPath("$.data.menus[?(@.path == '/system/processSet/templateSet')].hidden",
                        hasItem(true)))
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    @Test
    void validationRunsBeforeTheGate() throws Exception {
        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", jwtCreatorToken(101L, "route-validation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(
                        "菜单名称不能为空；路由地址不能为空；上级菜单不能为空；菜单类型不能为空"));

        mockMvc.perform(post("/admin/system/sysUser/save")
                        .header("token", jwtCreatorToken(101L, "route-validation"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(
                        "姓名不能为空；密码不能为空；用户名不能为空"));
    }

    @Test
    void workAndRecognitionRoutesStayAvailableWithoutBackofficeGrant() throws Exception {
        assertCode("/admin/process/findPending/1/10", 2L, "user", 200);
        assertCode("/admin/system/index/info", 2L, "user", 200);
    }

    private void assertCode(String path, long userId, String username, int code) throws Exception {
        mockMvc.perform(get(path).header("token", jwtHelper.createTokenPair(userId, username, 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }

    private String jwtCreatorToken(long userId, String username) {
        createUserIfMissing(userId, username);
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }

    private void assertOnlyMethod(Class<?> controller, String methodName, RequestMethod expected) {
        Set<RequestMethod> methods = new LinkedHashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (handler.getBeanType().equals(controller)
                    && handler.getMethod().getName().equals(methodName)) {
                methods.addAll(entry.getKey().getMethodsCondition().getMethods());
            }
        }
        assertEquals(Set.of(expected), methods,
                controller.getSimpleName() + "#" + methodName + " must be served only by " + expected);
    }

    private void grant(long roleId, long userId, String username, String menuPath) {
        createUserIfMissing(userId, username);
        Integer menuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = ? AND is_deleted = 0", Integer.class, menuPath);
        grantMenuId(roleId, userId, menuId);
    }

    private void grantPermission(long roleId, long userId, String username, String permission) {
        createUserIfMissing(userId, username);
        Integer menuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND type = 2 AND is_deleted = 0",
                Integer.class, permission);
        grantMenuId(roleId, userId, menuId);
    }

    private void grantMenuId(long roleId, long userId, Integer menuId) {
        if (count("SELECT COUNT(*) FROM sys_role WHERE id = " + roleId) == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_role (id, role_name, role_code, is_deleted) VALUES (?, ?, ?, 0)",
                    roleId, "route-" + roleId, "route_" + roleId);
            jdbcTemplate.update(
                    "INSERT INTO sys_user_role (role_id, user_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, userId);
        }
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                roleId, menuId);
    }

    private void createUserIfMissing(long userId, String username) {
        if (count("SELECT COUNT(*) FROM sys_user WHERE id = " + userId) == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (id, username, password, name, status, is_deleted) "
                            + "VALUES (?, ?, '', ?, 1, 0)", userId, username, username);
        }
    }

    private long templateId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM oa_process_template WHERE name = '请假申请'", Long.class);
    }

    private Integer userStatus(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM sys_user WHERE id = ?", Integer.class, id);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private void rejectsTheHttpMethod(MvcResult result) {
        assertInstanceOf(HttpRequestMethodNotSupportedException.class, result.getResolvedException());
    }

    private static boolean isWrite(RequestMappingInfo mapping) {
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        return methods.stream().anyMatch(method -> method != RequestMethod.GET);
    }

    private static Gate gateOf(HandlerMethod handler) {
        PreAuthorize gate = AnnotatedElementUtils.findMergedAnnotation(
                handler.getMethod(), PreAuthorize.class);
        if (gate == null) {
            return null;
        }
        // 一个端点可以接受「add 或 edit」两个按钮点（上传端点），仍必须服务同一个页面。
        String page = null;
        List<String> permissions = new ArrayList<>();
        for (String expression : gate.value().split(" or ")) {
            Matcher matcher = GATE.matcher(expression.trim());
            if (!matcher.matches()) {
                return null;
            }
            if (page == null) {
                page = matcher.group(1);
            } else if (!page.equals(matcher.group(1))) {
                return null;
            }
            if (matcher.group(2) != null) {
                permissions.add(matcher.group(2));
            }
        }
        return page == null ? null : new Gate(page, permissions);
    }

    private static String keyOf(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
    }

    private static void expect(Class<?> controller, String page, String permission, String... methods) {
        for (String method : methods) {
            EXPECTED_GATES.put(
                    controller.getSimpleName() + "#" + method,
                    permission == null
                            ? "@backofficeGate.isAllowed('" + page + "')"
                            : "@backofficeGate.isAllowed('" + page + "','" + permission + "')");
        }
    }

    /** 同一页面上的两个等价按钮点：端点接受任一即可。 */
    private static void expectEither(Class<?> controller, String page,
                                     String first, String second, String... methods) {
        String gate = "@backofficeGate.isAllowed('" + page + "','" + first + "') or "
                + "@backofficeGate.isAllowed('" + page + "','" + second + "')";
        for (String method : methods) {
            EXPECTED_GATES.put(controller.getSimpleName() + "#" + method, gate);
        }
    }

    private record Gate(String page, List<String> permissions) {
    }
}
