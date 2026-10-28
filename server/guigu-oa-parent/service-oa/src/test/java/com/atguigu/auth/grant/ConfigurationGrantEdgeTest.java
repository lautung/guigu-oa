package com.atguigu.auth.grant;

import com.atguigu.auth.grant.ConfigurationGrant.GrantedConfiguration;
import com.atguigu.auth.grant.ConfigurationGrant.GrantedMenu;
import com.atguigu.auth.mapper.SysMenuMapper;
import com.atguigu.model.system.SysMenu;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 关闭授予闭环里的防御分支：缺祖先、环、空路径、绝对路径与排除项。 */
class ConfigurationGrantEdgeTest {

    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final ConfigurationGrant grant = new ConfigurationGrant(menuMapper);

    @Test
    void nullUserHasEmptySnapshot() {
        assertFalse(grant.snapshot(null).hasAnyPage());
    }

    @Test
    void emptyConfigurationCoversNothing() {
        GrantedConfiguration empty = GrantedConfiguration.empty();

        assertFalse(empty.hasAnyPage());
        assertFalse(empty.covers(null));
        assertFalse(empty.covers("/system"));
    }

    @Test
    void ancestorThatWasNotLoadedStopsGrantability() {
        SysMenu page = page(3L, 99L, "sysUser", "system/sysUser/index", 1);
        stubMenus(List.of());
        stubGranted(List.of(page));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void menuWithoutIdIsIgnoredWhileLoadingTheTree() {
        SysMenu parent = directory(null, 0L, "/system");
        SysMenu page = page(3L, 1L, "sysUser", "system/sysUser/index", 1);
        stubMenus(List.of(parent));
        stubGranted(List.of(page));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void cyclicAncestorsAreNotGrantable() {
        SysMenu first = page(1L, 2L, "a", "system/a/index", 1);
        SysMenu second = page(2L, 1L, "b", "system/b/index", 1);
        stubMenus(List.of(first, second));
        stubGranted(List.of(first));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void pageWithoutParentIdIsNotGrantable() {
        SysMenu page = page(3L, null, "sysUser", "system/sysUser/index", 1);
        stubMenus(List.of());
        stubGranted(List.of(page));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void disabledAndBlankPagesAreNotGranted() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu disabled = page(2L, 1L, "sysUser", "system/sysUser/index", 0);
        SysMenu blank = page(3L, 1L, " ", "system/sysRole/index", 1);
        stubMenus(List.of(root, disabled, blank));
        stubGranted(List.of(root, disabled, blank));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void absolutePagePathIsNormalizedWhileTheRawPathStillCovers() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu page = page(2L, 1L, "/system//sysUser/", "system/sysUser/index", 1);
        stubMenus(List.of(root, page));
        stubGranted(List.of(page));

        GrantedConfiguration snapshot = grant.snapshot(7L);
        assertTrue(snapshot.covers("/system//sysUser/"));
        assertTrue(snapshot.entries().stream().anyMatch(entry -> entry.path().equals("/system/sysUser")));
    }

    @Test
    void relativePagePathJoinsDirectoryAncestorsAndDirectoriesHaveNoParentPath() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu page = page(2L, 1L, "sysUser/", "system/sysUser/index", 1);
        stubMenus(List.of(root, page));
        stubGranted(List.of(page));

        GrantedConfiguration snapshot = grant.snapshot(7L);
        GrantedMenu directoryEntry = entry(snapshot, "/system");
        assertNull(directoryEntry.parentPath());
        assertTrue(snapshot.entries().stream().anyMatch(entry -> entry.path().equals("/system/sysUser")));
    }

    @Test
    void hiddenPageWhoseVisibleParentIsMissingStaysInert() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu page = page(2L, 1L, "assignAuth", "system/sysRole/assignAuth", 1);
        SysMenu orphanHidden = page(3L, 99L, "orphanAuth", "system/sysRole/orphanAuth", 1);
        stubMenus(List.of(root, page, orphanHidden));
        stubGranted(List.of(orphanHidden));

        assertFalse(grant.snapshot(7L).hasAnyPage());
    }

    @Test
    void nullMenusAreNotConfigurationMenus() {
        stubMenus(List.of());

        assertFalse(grant.isConfigurationMenu((SysMenu) null));
        assertFalse(grant.isGrantableConfigurationMenu(null));
    }

    @Test
    void workPathsAreNeverConfigurationMenus() {
        assertFalse(grant.isConfigurationMenu(directory(1L, 0L, "work")));
        assertFalse(grant.isConfigurationMenu(directory(2L, 0L, "work/start")));
        assertFalse(grant.isConfigurationMenu(directory(3L, 0L, "/work/pending")));
        assertFalse(grant.isConfigurationMenu(directory(4L, 0L, "/grantTestOther")));
    }

    @Test
    void legacyExcludedComponentsAreNotConfigurationMenus() {
        SysMenu root = directory(1L, 0L, "/system");
        SysMenu operLog = page(2L, 1L, "sysOperLog", "system/sysoperlog/list", 1);
        stubMenus(List.of(root, operLog));

        assertFalse(grant.isConfigurationMenu(operLog));
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
        return menu;
    }

    private GrantedMenu entry(GrantedConfiguration snapshot, String path) {
        return snapshot.entries().stream()
                .filter(item -> item.path().equals(path))
                .findFirst()
                .orElseThrow();
    }
}
