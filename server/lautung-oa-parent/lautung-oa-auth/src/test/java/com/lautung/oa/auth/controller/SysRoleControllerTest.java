package com.lautung.oa.auth.controller;

import com.lautung.oa.auth.service.SysRoleService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.model.system.SysRole;
import com.lautung.oa.vo.system.AssginRoleVo;
import com.lautung.oa.vo.system.SysDeptOptionVo;
import com.lautung.oa.vo.system.SysRoleDetailVo;
import com.lautung.oa.vo.system.SysRoleQueryVo;
import com.lautung.oa.vo.system.SysRoleSaveRequest;
import com.lautung.oa.vo.system.SysRoleUpdateRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        when(service.removeById(3L)).thenReturn(true);
        when(service.removeByIds(List.of(3L, 4L))).thenReturn(false);

        SysRoleSaveRequest save = new SysRoleSaveRequest();
        save.setRoleName("审批员");
        save.setRoleCode("approver");
        save.setDataScope(2);
        save.setDeptIds(List.of(7L, 8L));
        SysRoleUpdateRequest update = new SysRoleUpdateRequest();
        update.setId(3L);
        update.setRoleName("审批员");

        assertEquals(200, controller.save(save).getCode());
        assertEquals(200, controller.update(update).getCode());
        assertEquals(200, controller.remove(3L).getCode());
        assertEquals(201, controller.batchRemove(List.of(3L, 4L)).getCode());

        ArgumentCaptor<SysRole> created = ArgumentCaptor.forClass(SysRole.class);
        ArgumentCaptor<SysRole> changed = ArgumentCaptor.forClass(SysRole.class);
        verify(service).saveRole(created.capture(), eq(List.of(7L, 8L)));
        verify(service).updateRole(changed.capture(), isNull());
        assertEquals("审批员", created.getValue().getRoleName());
        assertEquals(2, created.getValue().getDataScope());
        assertNull(created.getValue().getId());
        assertEquals(3L, changed.getValue().getId());
    }

    @Test
    void readsASingleRoleAsAnEditableDetail() {
        SysRoleDetailVo detail = new SysRoleDetailVo();
        detail.setId(3L);
        detail.setDataScope(2);
        detail.setDeptIds(List.of(7L));
        when(service.findDetail(3L)).thenReturn(detail);

        Result result = controller.get(3L);

        assertEquals(detail, result.getData());
    }

    @Test
    void listsActiveDeptOptions() {
        SysDeptOptionVo option = new SysDeptOptionVo();
        option.setId(7L);
        option.setName("研发部");
        when(service.findDeptOptions()).thenReturn(List.of(option));

        Result result = controller.depts();

        assertEquals(200, result.getCode());
        assertEquals(List.of(option), result.getData());
    }
}
