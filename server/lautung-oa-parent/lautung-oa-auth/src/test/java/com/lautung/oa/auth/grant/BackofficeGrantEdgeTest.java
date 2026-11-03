package com.lautung.oa.auth.grant;

import com.lautung.oa.auth.grant.BackofficeGrant.GrantedMenu;
import com.lautung.oa.auth.grant.BackofficeGrant.Snapshot;
import com.lautung.oa.auth.mapper.SysMenuMapper;
import com.lautung.oa.model.system.SysMenu;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 后台授予闭包的防御分支：缺祖先、环、空路径、绝对路径与按钮生效条件。 */
class BackofficeGrantEdgeTest {

    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final BackofficeGrant grant = new BackofficeGrant(menuMapper);

    @Test
    void nullUserHasEmptySnapshot() {
        assertFalse(grant.snapshot(null).hasAnyPage());
    }

    @Test
    void emptySnapshotCoversAndAllowsNothing() {
        Snapshot empty = Snapshot.empty();

        assertFalse(empty.hasAnyPage());
        assertFalse(empty.covers(null));
        assertFalse(empty.covers("/system"));
        assertFalse(empty.allows("/system/sysUser", null));
        assertTrue(empty.menus().isEmpty());
        assertTrue(empty.permissions().isEmpty());
    }

    @Test
    void ancestorThatWasNotLoadedStopsGrantability() {
        SysMenu page = page(3L, 99L, "/system/sysUser", "system/sysUser/index", 1);
        stubMenus(List.of());
        stubGranted(List.of(page));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void menuWithoutIdIsIgnoredWhileLoadingTheTree() {
        SysMenu parent = directory(null, 0L, "/system");
        SysMenu page = page(3L, 1L, "/system/sysUser", "system/sysUser/index", 1);
        stubMenus(List.of(parent));
        stubGranted(List.of(page));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void cyclicAncestorsAreNotGrantable() {
        SysMenu first = page(1L, 2L, "/system/a", "system/a/index", 1);
        SysMenu second = page(2L, 1L, "/system/b", "system/b/index", 1);
        stubMenus(List.of(first, second));
        stubGranted(List.of(first));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void pageWithoutParentIdIsNotGrantable() {
        SysMenu page = page(3L, null, "/system/sysUser", "system/sysUser/index", 1);
        stubMenus(List.of());
        stubGranted(List.of(page));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void disabledAndBlankPagesAreNotGranted() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu disabled = page(2L, 1L, "/system/sysUser", "system/sysUser/index", 0);
        SysMenu blank = page(3L, 1L, " ", "system/sysRole/index", 1);
        stubMenus(List.of(root, disabled, blank));
        stubGranted(List.of(root, disabled, blank));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void absolutePagePathIsNormalizedIntoTheCoveredSet() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu page = page(2L, 1L, "/system//sysUser/", "system/sysUser/index", 1);
        stubMenus(List.of(root, page));
        stubGranted(List.of(page));

        Snapshot snapshot = grant.snapshot(7L);
        assertTrue(snapshot.covers("/system/sysUser"));
        assertFalse(snapshot.covers("/system//sysUser/"));
        assertTrue(snapshot.menus().stream().anyMatch(entry -> entry.path().equals("/system/sysUser")));
    }

    @Test
    void relativePagePathJoinsDirectoryAncestorsAndDirectoriesHaveNoParentPath() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu page = page(2L, 1L, "sysUser/", "system/sysUser/index", 1);
        stubMenus(List.of(root, page));
        stubGranted(List.of(page));

        Snapshot snapshot = grant.snapshot(7L);
        assertTrue(snapshot.covers("/system/sysUser"));
        GrantedMenu directoryEntry = entry(snapshot, "/system");
        assertNull(directoryEntry.parentPath());
        assertTrue(snapshot.menus().stream().anyMatch(entry -> entry.path().equals("/system/sysUser")));
    }

    @Test
    void hiddenPageWhoseVisibleParentIsMissingStaysInert() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu page = page(2L, 1L, "/system/sysRole", "system/sysRole/index", 1);
        SysMenu orphanHidden = page(3L, 99L, "/system/orphanAuth", "system/sysRole/orphanAuth", 1);
        stubMenus(List.of(root, page, orphanHidden));
        stubGranted(List.of(orphanHidden));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void hiddenPageFollowsItsGrantedVisibleParentWithoutItsOwnGrant() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu page = page(2L, 1L, "/system/sysRole", "system/sysRole/index", 1);
        SysMenu hidden = page(3L, 2L, "/system/assignAuth", "system/sysRole/assignAuth", 1);
        stubMenus(List.of(root, page, hidden));
        stubGranted(List.of(page));

        Snapshot snapshot = grant.snapshot(7L);
        assertTrue(snapshot.covers("/system/assignAuth"));
        GrantedMenu hiddenEntry = entry(snapshot, "/system/assignAuth");
        assertTrue(hiddenEntry.hidden());
        assertEquals("/system/sysRole", hiddenEntry.parentPath());
    }

    @Test
    void buttonsEnterPermissionsOnlyWhenTheirPageIsGranted() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu page = page(2L, 1L, "/system/sysUser", "system/sysUser/index", 1);
        SysMenu button = button(3L, 2L, "system:user:add", 1);
        stubMenus(List.of(root, page, button));

        stubGranted(List.of(page, button));
        Snapshot both = grant.snapshot(7L);
        assertTrue(both.covers("/system/sysUser"));
        assertEquals(java.util.Set.of("system:user:add"), both.permissions());

        stubGranted(List.of(page));
        Snapshot pageOnly = grant.snapshot(7L);
        assertTrue(pageOnly.covers("/system/sysUser"));
        assertTrue(pageOnly.permissions().isEmpty());

        stubGranted(List.of(button));
        Snapshot buttonOnly = grant.snapshot(7L);
        assertFalse(buttonOnly.hasAnyPage());
        assertTrue(buttonOnly.permissions().isEmpty());
    }

    @Test
    void buttonsWithoutPermsAreNotPermissions() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu page = page(2L, 1L, "/system/sysUser", "system/sysUser/index", 1);
        SysMenu button = button(3L, 2L, null, 1);
        stubMenus(List.of(root, page, button));
        stubGranted(List.of(page, button));

        assertTrue(grant.snapshot(7L).permissions().isEmpty());
    }

    @Test
    void wholeMenuTableIsBackofficeRegardlessOfLegacyNames() {
        SysMenu work = directory(1L, 0L, "work");
        SysMenu wechat = directory(2L, 0L, "/wechat");
        SysMenu dashboard = page(3L, 2L, "dashboard", "dashboard/index", 1);
        SysMenu online = page(4L, 2L, "/onlineprocessset",
                "processSet/processTemplate/onlineProcessSet", 1);
        SysMenu operLog = page(5L, 2L, "sysOperLog", "system/sysoperlog/list", 1);
        stubMenus(List.of(work, wechat, dashboard, online, operLog));

        assertTrue(grant.isBackofficeMenu(work));
        assertTrue(grant.isBackofficeMenu(wechat));
        assertTrue(grant.isBackofficeMenu(dashboard));
        assertTrue(grant.isBackofficeMenu(online));
        assertTrue(grant.isBackofficeMenu(operLog));
    }

    @Test
    void typeOutsideZeroOneTwoIsNotBackoffice() {
        assertFalse(grant.isBackofficeMenu(menu(1L, 0L, "3", "", 3, 1)));
        assertFalse(grant.isBackofficeMenu((SysMenu) null));
        assertFalse(grant.isGrantableBackofficeMenu(null));
    }

    @Test
    void rootMustBeADirectory() {
        SysMenu pageAtRoot = page(1L, 0L, "/system", "system/index", 1);
        stubMenus(List.of(pageAtRoot));

        assertFalse(grant.isBackofficeMenu(pageAtRoot));
    }

    private void stubMenus(List<SysMenu> menus) {
        when(menuMapper.selectList(null)).thenReturn(menus);
    }

    private void stubGranted(List<SysMenu> menus) {
        when(menuMapper.findMenuListByUserId(7L)).thenReturn(menus);
    }

    private SysMenu directory(Long id, Long parentId, String path) {
        return menu(id, parentId, path, "Layout", 0, 1);
    }

    private SysMenu page(Long id, Long parentId, String path, String component, Integer status) {
        return menu(id, parentId, path, component, 1, status);
    }

    private SysMenu button(Long id, Long parentId, String perms, Integer status) {
        SysMenu menu = menu(id, parentId, "", "", 2, status);
        menu.setPerms(perms);
        return menu;
    }

    private SysMenu menu(Long id, Long parentId, String path, String component, Integer type,
                         Integer status) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setName(path);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setType(type);
        menu.setStatus(status);
        menu.setSortValue(1);
        menu.setIcon("el-icon-setting");
        return menu;
    }

    private GrantedMenu entry(Snapshot snapshot, String path) {
        return snapshot.menus().stream()
                .filter(item -> item.path().equals(path))
                .findFirst()
                .orElseThrow();
    }
}
