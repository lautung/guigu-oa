package com.atguigu.auth.controller;

import com.atguigu.auth.service.SysMenuService;
import com.atguigu.common.result.Result;
import com.atguigu.model.system.SysMenu;
import com.atguigu.vo.system.AssginMenuVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysMenuControllerTest {

    private final SysMenuService service = mock(SysMenuService.class);
    private final SysMenuController controller = new SysMenuController();

    @BeforeEach
    void injectService() {
        ReflectionTestUtils.setField(controller, "sysMenuService", service);
    }

    @Test
    void readsTheMenusAssignedToARole() {
        SysMenu menu = new SysMenu();
        menu.setId(3L);
        when(service.findMenuByRoleId(5L)).thenReturn(List.of(menu));

        Result result = controller.toAssign(5L);

        assertEquals(List.of(menu), result.getData());
    }

    @Test
    void forwardsMenuAssignments() {
        AssginMenuVo request = new AssginMenuVo();

        Result result = controller.doAssign(request);

        assertEquals(200, result.getCode());
        verify(service).doAssign(request);
    }

    @Test
    void listsMaintainableMenus() {
        when(service.findNodes()).thenReturn(List.of());

        Result result = controller.findNodes();

        assertEquals(200, result.getCode());
        assertEquals(List.of(), result.getData());
    }

    @Test
    void savesAndUpdatesMenus() {
        SysMenu menu = new SysMenu();

        controller.save(menu);
        controller.updateById(menu);

        verify(service).saveMenu(menu);
        verify(service).updateMenu(menu);
    }

    @Test
    void removesMenus() {
        controller.remove(3L);

        verify(service).removeMenuById(3L);
    }
}
