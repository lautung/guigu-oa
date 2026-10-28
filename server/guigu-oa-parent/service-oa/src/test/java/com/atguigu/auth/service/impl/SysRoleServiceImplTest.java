package com.atguigu.auth.service.impl;

import com.atguigu.auth.mapper.SysRoleMapper;
import com.atguigu.auth.service.PermissionCacheInvalidator;
import com.atguigu.auth.service.SysUserRoleService;
import com.atguigu.model.system.SysRole;
import com.atguigu.model.system.SysUserRole;
import com.atguigu.vo.system.AssginRoleVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysRoleServiceImplTest {

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysUserRoleService userRoleService = mock(SysUserRoleService.class);
    private final PermissionCacheInvalidator cacheInvalidator = mock(PermissionCacheInvalidator.class);
    private final SysRoleServiceImpl service = new SysRoleServiceImpl();

    @BeforeAll
    static void registerTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUserRole.class);
    }

    @BeforeEach
    void injectCollaborators() {
        ReflectionTestUtils.setField(service, "baseMapper", roleMapper);
        ReflectionTestUtils.setField(service, "sysUserRoleService", userRoleService);
        ReflectionTestUtils.setField(service, "permissionCacheInvalidator", cacheInvalidator);
    }

    @Test
    void updateInvalidatesRoleMembersOnlyWhenRowsChanged() {
        SysRole role = role(3L);
        when(roleMapper.updateById(role)).thenReturn(1);
        assertTrue(service.updateById(role));
        verify(cacheInvalidator).invalidateRoleMembersAfterCommit(3L);

        when(roleMapper.updateById(role)).thenReturn(0);
        assertFalse(service.updateById(role));
    }

    @Test
    void removeByIdInvalidatesOnlyOnSuccess() {
        when(roleMapper.deleteById(3L)).thenReturn(1);
        assertTrue(service.removeById((java.io.Serializable) 3L));
        verify(cacheInvalidator).invalidateRoleMembersAfterCommit(3L);

        when(roleMapper.deleteById(4L)).thenReturn(0);
        assertFalse(service.removeById((java.io.Serializable) 4L));
    }

    @Test
    void removeByEntityDelegatesToTheIdOverload() {
        assertFalse(service.removeById((SysRole) null));

        when(roleMapper.deleteById(5L)).thenReturn(1);
        assertTrue(service.removeById(role(5L)));
    }

    @Test
    void batchRemoveInvalidatesEveryNonNullId() {
        assertFalse(service.removeByIds(null));
        assertFalse(service.removeByIds(List.of()));

        when(roleMapper.deleteByIds(Arrays.asList(3L, null, 4L))).thenReturn(2);
        assertTrue(service.removeByIds(Arrays.asList(3L, null, 4L)));

        verify(cacheInvalidator).invalidateRoleMembersAfterCommit(3L);
        verify(cacheInvalidator).invalidateRoleMembersAfterCommit(4L);
    }

    @Test
    void batchRemoveReportsNothingRemoved() {
        when(roleMapper.deleteByIds(List.of(3L))).thenReturn(0);
        assertFalse(service.removeByIds(List.of(3L)));
        verify(cacheInvalidator, never()).invalidateRoleMembersAfterCommit(any());
    }

    @Test
    void findRoleDataSplitsAssignedFromAllRoles() {
        SysRole first = role(1L);
        SysRole second = role(2L);
        when(roleMapper.selectList(null)).thenReturn(List.of(first, second));
        SysUserRole assignment = new SysUserRole();
        assignment.setRoleId(2L);
        when(userRoleService.list(org.mockito.ArgumentMatchers.<Wrapper<SysUserRole>>any()))
                .thenReturn(List.of(assignment));

        Map<String, Object> data = service.findRoleDataByUserId(7L);

        assertEquals(List.of(second), data.get("assginRoleList"));
        assertEquals(List.of(first, second), data.get("allRolesList"));
    }

    @Test
    void doAssignRewritesRolesAndInvalidatesTheUser() {
        AssginRoleVo request = new AssginRoleVo();
        request.setUserId(7L);
        request.setRoleIdList(Arrays.asList(1L, null, 2L));

        service.doAssign(request);

        verify(userRoleService).remove(any());
        verify(userRoleService, org.mockito.Mockito.times(2)).save(any(SysUserRole.class));
        verify(cacheInvalidator).invalidateUserAfterCommit(7L);
    }

    private SysRole role(Long id) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleName("角色" + id);
        return role;
    }
}
