package com.atguigu.auth;

import com.atguigu.auth.grant.ConfigurationGrant;
import com.atguigu.auth.grant.ConfigurationGrant.GrantedConfiguration;
import com.atguigu.auth.grant.ConfigurationGrant.GrantedMenu;
import com.atguigu.auth.service.SysMenuService;
import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.model.system.SysMenu;
import com.atguigu.vo.system.AssginMenuVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_config_grant;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class ConfigurationGrantTest {

    private static final long USER_ID = 201L;
    private static final long ROLE_ID = 201L;

    @Autowired
    private ConfigurationGrant configurationGrant;

    @Autowired
    private SysMenuService sysMenuService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE id >= 9000");
    }

    @Test
    void leafOnlyGrantRendersAncestorsAndDoesNotGrantSiblings() {
        long userId = createUserAndRole();
        grantPaths("sysUser");

        GrantedConfiguration snapshot = configurationGrant.snapshot(userId);

        assertTrue(snapshot.hasAnyPage());
        assertTrue(snapshot.covers("sysUser"));
        assertFalse(snapshot.covers("sysRole"));
        assertEquals(Set.of("/system", "/system/sysUser"), new HashSet<>(entryPaths(snapshot)));
        assertEquals("/system", entry(snapshot, "/system/sysUser").parentPath());
    }

    @Test
    void directoryOnlyGrantHasNoConfigurationPermission() {
        long userId = createUserAndRole();
        grantPaths("/system");

        GrantedConfiguration snapshot = configurationGrant.snapshot(userId);

        assertFalse(snapshot.hasAnyPage());
        assertTrue(snapshot.entries().isEmpty());
        assertFalse(snapshot.covers("/system"));
    }

    @Test
    void hiddenPageFollowsItsVisibleParentEvenWhenNotGrantedItself() {
        long userId = createUserAndRole();
        grantPaths("sysRole");

        GrantedConfiguration snapshot = configurationGrant.snapshot(userId);

        assertTrue(snapshot.hasAnyPage());
        assertTrue(snapshot.covers("sysRole"));
        assertTrue(snapshot.covers("assignAuth"));
        GrantedMenu hidden = entry(snapshot, "/system/assignAuth");
        assertTrue(hidden.hidden());
        assertEquals("/system/sysRole", hidden.parentPath());
    }

    @Test
    void hiddenPageGrantedAloneIsInert() {
        long userId = createUserAndRole();
        grantPaths("assignAuth");

        GrantedConfiguration snapshot = configurationGrant.snapshot(userId);

        assertFalse(snapshot.hasAnyPage());
        assertTrue(snapshot.entries().isEmpty());
        assertFalse(snapshot.covers("assignAuth"));
    }

    @Test
    void buttonsStayOutsideConfigurationMenus() {
        SysMenu button = new SysMenu();
        button.setParentId(menuId("sysUser"));
        button.setName("按钮");
        button.setType(2);
        button.setPath("grantTestButton");
        button.setComponent("");
        button.setStatus(1);

        assertFalse(configurationGrant.isConfigurationMenu(button));
        assertFalse(configurationGrant.isGrantableConfigurationMenu(button));
    }

    @Test
    void disabledPageIsMaintainableButNotGrantable() {
        SysMenu disabled = new SysMenu();
        disabled.setParentId(menuId("/system"));
        disabled.setName("停用页面");
        disabled.setType(1);
        disabled.setPath("grantTestDisabled");
        disabled.setComponent("system/grantTestDisabled/list");
        disabled.setStatus(0);

        assertTrue(configurationGrant.isConfigurationMenu(disabled));
        assertFalse(configurationGrant.isGrantableConfigurationMenu(disabled));
    }

    @Test
    void configurationMenusExcludeButtonsAndNonConfigurationRoots() {
        insertMenu(9001, menuId("sysUser"), "grantTestButton", "", 2, 1);
        insertMenu(9010, 0, "/grantTestWork", "Layout", 0, 1);
        insertMenu(9011, 9010, "pending", "work/pending/index", 1, 1);

        List<String> paths = configurationGrant.configurationMenus().stream()
                .map(SysMenu::getPath)
                .toList();

        assertTrue(paths.contains("sysUser"));
        assertFalse(paths.contains("grantTestButton"));
        assertFalse(paths.contains("/grantTestWork"));
        assertFalse(paths.contains("pending"));
    }

    @Test
    void excludedProductMenusStayOutsideConfigurationMenus() {
        long systemId = menuId("/system");
        insertMenu(9020, systemId, "grantTestWechat", "wechat/menu/list", 1, 1);
        insertMenu(9021, systemId, "grantTestDashboard", "dashboard/index", 1, 1);
        insertMenu(9022, systemId, "grantTestOnline", "processSet/processTemplate/onlineProcessSet", 1, 1);

        List<String> paths = configurationGrant.configurationMenus().stream()
                .map(SysMenu::getPath)
                .toList();

        assertFalse(paths.contains("grantTestWechat"));
        assertFalse(paths.contains("grantTestDashboard"));
        assertFalse(paths.contains("grantTestOnline"));
    }

    @Test
    void menuMaintenanceRejectsNonConfigurationMenus() {
        SysMenu button = new SysMenu();
        button.setParentId(menuId("sysUser"));
        button.setName("按钮");
        button.setType(2);
        button.setPath("grantTestButton");
        button.setComponent("");
        button.setStatus(1);

        GuiguException error = assertThrows(GuiguException.class, () -> sysMenuService.saveMenu(button));

        assertTrue(error.getMessage().contains("只能维护配置菜单"));
    }

    @Test
    void doAssignSkipsAnExistingLegacyGrantAndRewritesWithConfigurationMenus() {
        createUserAndRole();
        long buttonId = insertMenu(9001, menuId("sysUser"), "grantTestButton", "", 2, 1);
        grantMenuIds(buttonId);

        AssginMenuVo request = new AssginMenuVo();
        request.setRoleId(ROLE_ID);
        request.setMenuIdList(List.of(buttonId, menuId("sysUser")));

        sysMenuService.doAssign(request);

        List<Long> remaining = jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ? AND is_deleted = 0",
                Long.class,
                ROLE_ID);
        assertEquals(List.of(menuId("sysUser")), remaining);
    }

    private long createUserAndRole() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, name, status, is_deleted) "
                        + "VALUES (?, ?, '', ?, 1, 0)",
                USER_ID, "grant-test-user", "授予测试");
        jdbcTemplate.update(
                "INSERT INTO sys_role (id, role_name, role_code, is_deleted) VALUES (?, ?, ?, 0)",
                ROLE_ID, "授予测试", "grant_test_role");
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (role_id, user_id, is_deleted) VALUES (?, ?, 0)",
                ROLE_ID, USER_ID);
        return USER_ID;
    }

    private void grantPaths(String... paths) {
        for (String path : paths) {
            grantMenuIds(menuId(path));
        }
    }

    private void grantMenuIds(long... menuIds) {
        for (long menuId : menuIds) {
            jdbcTemplate.update(
                    "INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    ROLE_ID, menuId);
        }
    }

    private long insertMenu(long id, long parentId, String path, String component, int type, int status) {
        jdbcTemplate.update(
                "INSERT INTO sys_menu (id, parent_id, name, type, path, component, perms, icon, sort_value, "
                        + "status, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, '', '', 99, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                id, parentId, path, type, path, component, status);
        return id;
    }

    private long menuId(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = ? AND is_deleted = 0",
                Long.class,
                path);
    }

    private List<String> entryPaths(GrantedConfiguration snapshot) {
        return snapshot.entries().stream().map(GrantedMenu::path).toList();
    }

    private GrantedMenu entry(GrantedConfiguration snapshot, String path) {
        return snapshot.entries().stream()
                .filter(item -> item.path().equals(path))
                .findFirst()
                .orElseThrow();
    }
}
