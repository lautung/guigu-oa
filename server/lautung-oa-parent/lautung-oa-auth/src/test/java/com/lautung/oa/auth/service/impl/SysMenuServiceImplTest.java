package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.grant.ConfigurationGrant;
import com.lautung.oa.auth.mapper.SysMenuMapper;
import com.lautung.oa.auth.service.PermissionCacheInvalidator;
import com.lautung.oa.auth.service.SysRoleMenuService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysMenu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * updateMenu 的落库形状判定（ADR 0036）：目录转成菜单页而组件仍为空时拒绝。
 * 显式送空白的组件路径已在 HTTP 边界被 DTO 拒绝；这里只判「请求不发 component、沿用旧行」的形状。
 */
class SysMenuServiceImplTest {

    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final SysRoleMenuService roleMenuService = mock(SysRoleMenuService.class);
    private final PermissionCacheInvalidator cacheInvalidator = mock(PermissionCacheInvalidator.class);
    private final ConfigurationGrant configurationGrant = mock(ConfigurationGrant.class);
    private final SysMenuServiceImpl service = new SysMenuServiceImpl();

    @BeforeEach
    void injectCollaborators() {
        ReflectionTestUtils.setField(service, "baseMapper", menuMapper);
        ReflectionTestUtils.setField(service, "sysRoleMenuService", roleMenuService);
        ReflectionTestUtils.setField(service, "permissionCacheInvalidator", cacheInvalidator);
        ReflectionTestUtils.setField(service, "configurationGrant", configurationGrant);
    }

    @Test
    void directoryConvertedToPageWithoutComponentIsRejected() {
        SysMenu existing = directory(9L, "审批查询");
        SysMenu update = new SysMenu();
        update.setId(9L);
        update.setType(1);
        update.setName("审批查询页");
        when(configurationGrant.isConfigurationMenu(update)).thenReturn(true);
        when(menuMapper.selectById(9L)).thenReturn(existing);

        BusinessException error = assertThrows(BusinessException.class, () -> service.updateMenu(update));

        assertEquals(201, error.getCode());
        assertEquals("类型为菜单时组件路径不能为空", error.getMsg());
        verify(menuMapper).selectById(9L);
    }

    @Test
    void pageUpdateWithoutComponentKeepsTheExistingComponent() {
        SysMenu existing = page(4L, "部门管理", "system/sysdept/list");
        SysMenu update = new SysMenu();
        update.setId(4L);
        update.setType(1);
        update.setName("部门管理改名");
        when(configurationGrant.isConfigurationMenu(update)).thenReturn(true);
        when(menuMapper.selectById(4L)).thenReturn(existing);
        when(menuMapper.updateById(update)).thenReturn(1);

        service.updateMenu(update);

        verify(menuMapper).updateById(update);
    }

    @Test
    void buttonMustHangUnderAPage() {
        SysMenu button = button(0L);
        when(configurationGrant.isConfigurationMenu(button)).thenReturn(true);
        when(menuMapper.selectById(0L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveMenu(button));

        assertEquals(201, error.getCode());
        assertEquals("按钮必须挂在页面下", error.getMsg());
    }

    @Test
    void buttonWithPageParentIsSaved() {
        SysMenu button = button(4L);
        when(configurationGrant.isConfigurationMenu(button)).thenReturn(true);
        when(menuMapper.selectById(4L)).thenReturn(page(4L, "用户管理", "system/sysUser/list"));
        when(menuMapper.insert(button)).thenReturn(1);

        service.saveMenu(button);

        verify(menuMapper).insert(button);
    }

    @Test
    void buttonUpdateCanKeepTheExistingPermsAndParent() {
        SysMenu existing = button(4L);
        existing.setId(8L);
        SysMenu update = new SysMenu();
        update.setId(8L);
        update.setType(2);
        update.setName("新增用户改名");
        when(configurationGrant.isConfigurationMenu(update)).thenReturn(true);
        when(menuMapper.selectById(8L)).thenReturn(existing);
        when(menuMapper.selectById(4L)).thenReturn(page(4L, "用户管理", "system/sysUser/list"));
        when(menuMapper.updateById(update)).thenReturn(1);

        service.updateMenu(update);

        verify(menuMapper).updateById(update);
    }

    private SysMenu button(Long parentId) {
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setName("新增用户");
        menu.setType(2);
        menu.setPath("");
        menu.setComponent("");
        menu.setPerms("system:user:add");
        menu.setStatus(1);
        return menu;
    }

    private SysMenu directory(Long id, String name) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(0L);
        menu.setName(name);
        menu.setType(0);
        menu.setPath("query");
        menu.setComponent("");
        return menu;
    }

    private SysMenu page(Long id, String name, String component) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(1L);
        menu.setName(name);
        menu.setType(1);
        menu.setPath("deptList");
        menu.setComponent(component);
        return menu;
    }
}
