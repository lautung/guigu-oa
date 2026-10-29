package com.atguigu.auth.controller;

import com.atguigu.auth.service.SysRoleService;
import com.atguigu.common.result.Result;
import com.atguigu.model.system.SysRole;
import com.atguigu.vo.system.AssginRoleVo;
import com.atguigu.vo.system.SysRoleQueryVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysRoleControllerTest {

    private final SysRoleService service = mock(SysRoleService.class);
    private final SysRoleController controller = new SysRoleController();

    @BeforeEach
    void injectService() {
        ReflectionTestUtils.setField(controller, "sysRoleService", service);
    }

    @Test
    void assignsRolesForAUser() {
        when(service.findRoleDataByUserId(5L)).thenReturn(Map.of("allRolesList", List.of()));

        Result result = controller.toAssign(5L);

        assertEquals(200, result.getCode());
        assertEquals(Map.of("allRolesList", List.of()), result.getData());
    }

    @Test
    void writesRoleAssignments() {
        AssginRoleVo request = new AssginRoleVo();

        Result result = controller.doAssign(request);

        assertEquals(200, result.getCode());
        verify(service).doAssign(request);
    }

    @Test
    void findAllReturnsRoles() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleName("配置管理员");
        when(service.list()).thenReturn(List.of(role));

        Result result = controller.findAll();

        assertEquals(200, result.getCode());
        assertEquals(List.of(role), result.getData());
    }

    @Test
    void pagesRolesWithAndWithoutANameFilter() {
        when(service.page(any(), any())).thenReturn(new Page<>(1, 10));

        SysRoleQueryVo blank = new SysRoleQueryVo();
        assertEquals(200, controller.pageQueryRole(1L, 10L, blank).getCode());

        SysRoleQueryVo filtered = new SysRoleQueryVo();
        filtered.setRoleName("审批");
        Result result = controller.pageQueryRole(1L, 10L, filtered);

        assertEquals(200, result.getCode());
        assertTrue(result.getData() instanceof Page);
    }

    @Test
    void reportsWriteOutcomes() {
        SysRole role = new SysRole();
        role.setId(3L);

        when(service.save(role)).thenReturn(true);
        when(service.updateById(role)).thenReturn(false);
        when(service.removeById(3L)).thenReturn(true);
        when(service.removeByIds(List.of(3L, 4L))).thenReturn(false);

        assertEquals(200, controller.save(role).getCode());
        assertEquals(201, controller.update(role).getCode());
        assertEquals(200, controller.remove(3L).getCode());
        assertEquals(201, controller.batchRemove(List.of(3L, 4L)).getCode());
    }

    @Test
    void readsASingleRole() {
        SysRole role = new SysRole();
        role.setId(3L);
        when(service.getById(3L)).thenReturn(role);

        Result result = controller.get(3L);

        assertEquals(role, result.getData());
    }
}
