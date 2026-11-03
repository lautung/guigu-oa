package com.lautung.oa.auth;

import com.lautung.oa.auth.grant.BackofficeGrant;
import com.lautung.oa.auth.grant.BackofficeGrant.GrantedMenu;
import com.lautung.oa.auth.grant.BackofficeGrant.Snapshot;
import com.lautung.oa.auth.service.SysMenuService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysMenu;
import com.lautung.oa.vo.system.AssginMenuVo;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_backoffice_grant;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class BackofficeGrantTest {

    private static final long USER_ID = 201L;
    private static final long ROLE_ID = 201L;

    @Autowired
    private BackofficeGrant backofficeGrant;

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
        grantPaths("/system/sysUser");

        Snapshot snapshot = backofficeGrant.snapshot(userId);

        assertTrue(snapshot.hasAnyPage());
        assertTrue(snapshot.covers("/system/sysUser"));
        assertFalse(snapshot.covers("/system/sysRole"));
        assertFalse(snapshot.covers("/system"), "目录不构成后台权限");
        assertEquals(Set.of("/system", "/system/sysUser"), new HashSet<>(entryPaths(snapshot)));
        assertEquals("/system", entry(snapshot, "/system/sysUser").parentPath());
    }

    @Test
    void directoryOnlyGrantHasNoBackofficePermission() {
        long userId = createUserAndRole();
        grantPaths("/system");

        Snapshot snapshot = backofficeGrant.snapshot(userId);

        assertFalse(snapshot.hasAnyPage());
        assertTrue(snapshot.menus().isEmpty());
        assertTrue(snapshot.permissions().isEmpty());
        assertFalse(snapshot.covers("/system"));
    }

    @Test
    void buttonOnlyGrantDoesNotGrantItsPage() {
        long userId = createUserAndRole();
        grantMenuIds(buttonId("system:user:add"));

        Snapshot snapshot = backofficeGrant.snapshot(userId);

        assertFalse(snapshot.hasAnyPage());
        assertTrue(snapshot.menus().isEmpty());
        assertTrue(snapshot.permissions().isEmpty());
        assertFalse(snapshot.covers("/system/sysUser"));
    }

    @Test
    void pageGrantAloneHasNoButtonPermissions() {
        long userId = createUserAndRole();
        grantPaths("/system/sysUser");

        Snapshot snapshot = backofficeGrant.snapshot(userId);

        assertTrue(snapshot.covers("/system/sysUser"));
        assertTrue(snapshot.permissions().isEmpty());
    }

    @Test
    void pageAndItsButtonYieldThePermission() {
        long userId = createUserAndRole();
        grantPaths("/system/sysUser");
        grantMenuIds(buttonId("system:user:add"));

        Snapshot snapshot = backofficeGrant.snapshot(userId);

        assertEquals(Set.of("system:user:add"), snapshot.permissions());
        assertTrue(snapshot.allows("/system/sysUser", "system:user:add"));
        assertFalse(snapshot.allows("/system/sysUser", "system:user:remove"));
        assertFalse(snapshot.allows("/system/sysRole", null));
    }

    @Test
    void aButtonOfAnotherPageStaysInactive() {
        long userId = createUserAndRole();
        grantPaths("/system/sysRole");
        grantMenuIds(buttonId("system:user:add"));

        Snapshot snapshot = backofficeGrant.snapshot(userId);

        assertTrue(snapshot.covers("/system/sysRole"));
        assertTrue(snapshot.permissions().isEmpty());
    }

    @Test
    void hiddenPageFollowsItsVisibleParentEvenWhenNotGrantedItself() {
        long userId = createUserAndRole();
        grantPaths("/system/sysRole");

        Snapshot snapshot = backofficeGrant.snapshot(userId);

        assertTrue(snapshot.hasAnyPage());
        assertTrue(snapshot.covers("/system/sysRole"));
        assertTrue(snapshot.covers("/system/assignAuth"));
        GrantedMenu hidden = entry(snapshot, "/system/assignAuth");
        assertTrue(hidden.hidden());
        assertEquals("/system/sysRole", hidden.parentPath());
        assertEquals(1, hidden.type());
    }

    @Test
    void hiddenPageGrantedAloneIsInert() {
        long userId = createUserAndRole();
        grantPaths("/system/assignAuth");

        Snapshot snapshot = backofficeGrant.snapshot(userId);

        assertFalse(snapshot.hasAnyPage());
        assertTrue(snapshot.menus().isEmpty());
        assertFalse(snapshot.covers("/system/assignAuth"));
    }

    @Test
    void menusCarryTypeZeroAndOneOnlyWhilePermissionsCarryButtons() {
        long userId = createUserAndRole();
        grantPaths("/system/sysUser");
        grantMenuIds(buttonId("system:user:add"));

        Snapshot snapshot = backofficeGrant.snapshot(userId);

        assertTrue(snapshot.menus().stream().allMatch(menu -> menu.type() == 0 || menu.type() == 1));
        assertFalse(snapshot.menus().stream().anyMatch(menu -> menu.type() == 2));
        assertEquals(Set.of("system:user:add"), snapshot.permissions());
    }

    @Test
    void grantedMenuEntriesCarryAbsolutePathsAndIcons() {
        long userId = createUserAndRole();
        insertMenu(9001, menuId("/system"), "/system/iconPage", "system/icon/list", 1, 1);
        grantMenuIds(9001);

        GrantedMenu entry = entry(backofficeGrant.snapshot(userId), "/system/iconPage");

        assertEquals("/system", entry.parentPath());
        assertEquals("el-icon-setting", entry.icon());
    }

    @Test
    void buttonsAreBackofficeMenusUnderTheirPage() {
        SysMenu button = new SysMenu();
        button.setParentId(menuId("/system/sysUser"));
        button.setName("按钮");
        button.setType(2);
        button.setPath("");
        button.setComponent("");
        button.setPerms("system:user:grantTest");
        button.setStatus(1);

        assertTrue(backofficeGrant.isBackofficeMenu(button));
        assertTrue(backofficeGrant.isGrantableBackofficeMenu(button));
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

        assertTrue(backofficeGrant.isBackofficeMenu(disabled));
        assertFalse(backofficeGrant.isGrantableBackofficeMenu(disabled));
    }

    @Test
    void wholeMenuTableIsBackofficeAndTypeThreeIsNot() {
        long systemId = menuId("/system");
        long workRootId = insertMenu(9010, 0, "/grantTestWork", "Layout", 0, 1);
        insertMenu(9011, workRootId, "pending", "work/pending/index", 1, 1);
        insertMenu(9012, systemId, "grantTestWechat", "wechat/menu/list", 1, 1);
        insertMenu(9013, systemId, "grantTestDashboard", "dashboard/index", 1, 1);
        insertMenu(9014, systemId, "grantTestOnline",
                "processSet/processTemplate/onlineProcessSet", 1, 1);
        insertMenu(9015, systemId, "grantTestTypeThree", "system/grantTest/list", 3, 1);

        List<String> paths = backofficeGrant.backofficeMenus().stream()
                .map(SysMenu::getPath)
                .toList();

        assertTrue(paths.contains("/grantTestWork"));
        assertTrue(paths.contains("pending"));
        assertTrue(paths.contains("grantTestWechat"));
        assertTrue(paths.contains("grantTestDashboard"));
        assertTrue(paths.contains("grantTestOnline"));
        assertFalse(paths.contains("grantTestTypeThree"));

        SysMenu workRoot = new SysMenu();
        workRoot.setParentId(0L);
        workRoot.setName("办事");
        workRoot.setType(0);
        workRoot.setPath("/grantTestWork");
        workRoot.setComponent("Layout");
        workRoot.setStatus(1);
        assertTrue(backofficeGrant.isBackofficeMenu(workRoot));
    }

    @Test
    void menuMaintenanceRejectsNonBackofficeTypes() {
        SysMenu typeThree = new SysMenu();
        typeThree.setParentId(0L);
        typeThree.setName("坏类型");
        typeThree.setType(3);
        typeThree.setPath("grantTestWork");
        typeThree.setComponent("work/pending/index");
        typeThree.setStatus(1);

        BusinessException error = assertThrows(BusinessException.class, () -> sysMenuService.saveMenu(typeThree));

        assertTrue(error.getMessage().contains("只能维护配置菜单"));
    }

    @Test
    void doAssignDropsAnExistingInvalidGrantAndRewritesWithBackofficeMenus() {
        createUserAndRole();
        long invalidId = insertMenu(9010, 0, "/grantTestInvalid", "", 3, 1);
        grantMenuIds(invalidId);

        AssginMenuVo request = new AssginMenuVo();
        request.setRoleId(ROLE_ID);
        request.setMenuIdList(List.of(invalidId, menuId("/system/sysUser")));

        sysMenuService.doAssign(request);

        List<Long> remaining = jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ? AND is_deleted = 0",
                Long.class,
                ROLE_ID);
        assertEquals(List.of(menuId("/system/sysUser")), remaining);
    }

    @Test
    void doAssignRejectsANewInvalidIdWithoutTouchingExistingGrants() {
        createUserAndRole();
        long invalidId = insertMenu(9010, 0, "/grantTestInvalid", "", 3, 1);
        grantPaths("/system/sysUser");

        AssginMenuVo request = new AssginMenuVo();
        request.setRoleId(ROLE_ID);
        request.setMenuIdList(List.of(menuId("/system/sysUser"), invalidId));

        BusinessException error = assertThrows(BusinessException.class, () -> sysMenuService.doAssign(request));

        assertEquals(201, error.getCode());
        assertTrue(error.getMessage().contains("只能授予配置菜单"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = ? AND menu_id = ? AND is_deleted = 0",
                Integer.class, ROLE_ID, menuId("/system/sysUser")));
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
                        + "VALUES (?, ?, ?, ?, ?, ?, '', 'el-icon-setting', 99, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                id, parentId, path, type, path, component, status);
        return id;
    }

    private long menuId(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = ? AND is_deleted = 0",
                Long.class,
                path);
    }

    private long buttonId(String perms) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND type = 2 AND is_deleted = 0",
                Long.class,
                perms);
    }

    private List<String> entryPaths(Snapshot snapshot) {
        return snapshot.menus().stream().map(GrantedMenu::path).toList();
    }

    private GrantedMenu entry(Snapshot snapshot, String path) {
        return snapshot.menus().stream()
                .filter(item -> item.path().equals(path))
                .findFirst()
                .orElseThrow();
    }
}
