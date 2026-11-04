package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.mapper.SysDeptMapper;
import com.lautung.oa.auth.mapper.SysRoleDeptMapper;
import com.lautung.oa.auth.mapper.SysRoleMapper;
import com.lautung.oa.auth.service.PermissionCacheInvalidator;
import com.lautung.oa.auth.service.SysRoleMenuService;
import com.lautung.oa.auth.service.SysUserRoleService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysDept;
import com.lautung.oa.model.system.SysRole;
import com.lautung.oa.model.system.SysRoleDept;
import com.lautung.oa.model.system.SysUserRole;
import com.lautung.oa.vo.system.AssginRoleVo;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysRoleServiceImplTest {

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysUserRoleService userRoleService = mock(SysUserRoleService.class);
    private final SysRoleMenuService roleMenuService = mock(SysRoleMenuService.class);
    private final SysRoleDeptMapper roleDeptMapper = mock(SysRoleDeptMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final PermissionCacheInvalidator cacheInvalidator = mock(PermissionCacheInvalidator.class);
    private final SysRoleServiceImpl service = new SysRoleServiceImpl();

    @BeforeAll
    static void registerTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, SysRoleDept.class);
        TableInfoHelper.initTableInfo(assistant, SysDept.class);
    }

    @BeforeEach
    void injectCollaborators() {
        ReflectionTestUtils.setField(service, "baseMapper", roleMapper);
        ReflectionTestUtils.setField(service, "sysUserRoleService", userRoleService);
        ReflectionTestUtils.setField(service, "sysRoleMenuService", roleMenuService);
        ReflectionTestUtils.setField(service, "sysRoleDeptMapper", roleDeptMapper);
        ReflectionTestUtils.setField(service, "sysDeptMapper", deptMapper);
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
    void removeByIdClearsGrantRowsAndInvalidatesPreEnumeratedMembers() {
        when(cacheInvalidator.membersOfRole(3L)).thenReturn(Set.of(7L, 8L));
        when(roleMapper.deleteById(3L)).thenReturn(1);
        assertTrue(service.removeById((java.io.Serializable) 3L));

        verify(userRoleService).remove(any());
        verify(roleMenuService).remove(any());
        verify(cacheInvalidator).invalidateUsersAfterCommit(Set.of(7L, 8L));
        verify(cacheInvalidator, never()).invalidateRoleMembersAfterCommit(any());

        when(roleMapper.deleteById(4L)).thenReturn(0);
        assertFalse(service.removeById((java.io.Serializable) 4L));
        // 删行失败时不回收关系、不清缓存：成员仍挂在角色上。
        verify(userRoleService, org.mockito.Mockito.times(1)).remove(any());
        verify(roleMenuService, org.mockito.Mockito.times(1)).remove(any());
        verify(cacheInvalidator, org.mockito.Mockito.times(1)).invalidateUsersAfterCommit(any());
    }

    @Test
    void removeByEntityDelegatesToTheIdOverload() {
        assertFalse(service.removeById((SysRole) null));

        when(roleMapper.deleteById(5L)).thenReturn(1);
        assertTrue(service.removeById(role(5L)));
        verify(roleMenuService).remove(any());
    }

    @Test
    void batchRemoveRejectsAnEmptyListWithANamedMessage() {
        // 空列表（含全 null 元素）是明确拒绝而非幂等无操作，与单删删不到行同为 201 家族（#156，ADR 0040）。
        BusinessException empty = assertThrows(BusinessException.class, () -> service.removeByIds(List.of()));
        BusinessException nullList = assertThrows(BusinessException.class, () -> service.removeByIds(null));
        BusinessException nullOnly = assertThrows(BusinessException.class, () -> service.removeByIds(Arrays.asList(null, null)));

        assertEquals(201, empty.getCode());
        assertEquals("删除列表不能为空", empty.getMessage());
        assertEquals(201, nullList.getCode());
        assertEquals("删除列表不能为空", nullList.getMessage());
        assertEquals(201, nullOnly.getCode());
        assertEquals("删除列表不能为空", nullOnly.getMessage());
        verify(cacheInvalidator, never()).invalidateUsersAfterCommit(any());
    }

    @Test
    void batchRemoveClearsGrantRowsAndInvalidatesTheMemberUnion() {
        when(cacheInvalidator.membersOfRole(3L)).thenReturn(Set.of(7L));
        when(cacheInvalidator.membersOfRole(4L)).thenReturn(Set.of(7L, 9L));
        when(roleMapper.deleteByIds(Arrays.asList(3L, 4L))).thenReturn(2);
        assertTrue(service.removeByIds(Arrays.asList(3L, null, 4L)));

        verify(roleMapper).deleteByIds(Arrays.asList(3L, 4L));
        verify(userRoleService, org.mockito.Mockito.times(2)).remove(any());
        verify(roleMenuService, org.mockito.Mockito.times(2)).remove(any());
        verify(cacheInvalidator).invalidateUsersAfterCommit(new java.util.LinkedHashSet<>(Arrays.asList(7L, 9L)));

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
        when(roleMapper.selectById(1L)).thenReturn(role(1L));
        when(roleMapper.selectById(2L)).thenReturn(role(2L));
        AssginRoleVo request = new AssginRoleVo();
        request.setUserId(7L);
        request.setRoleIdList(Arrays.asList(1L, null, 2L));

        service.doAssign(request);

        // 用户侧与角色侧写同一关系表：整体替换走 replaceUserRoles，空值被过滤、重复值去重。
        verify(userRoleService).replaceUserRoles(7L, List.of(1L, 2L));
        verify(cacheInvalidator).invalidateUserAfterCommit(7L);
    }

    @Test
    void doAssignRejectsAnUnknownRoleWithoutTouchingAssignments() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L));
        AssginRoleVo request = new AssginRoleVo();
        request.setUserId(7L);
        request.setRoleIdList(Arrays.asList(1L, 999999L));

        BusinessException error = assertThrows(BusinessException.class, () -> service.doAssign(request));

        assertEquals(201, error.getCode());
        assertTrue(error.getMessage().contains("只能分配已存在的角色"));
        verify(userRoleService, never()).replaceUserRoles(any(), any());
        verify(userRoleService, never()).save(any(SysUserRole.class));
        verify(cacheInvalidator, never()).invalidateUserAfterCommit(any());
    }

    @Test
    void doAssignTreatsAMissingRoleListAsClearingAssignments() {
        AssginRoleVo request = new AssginRoleVo();
        request.setUserId(7L);

        service.doAssign(request);

        // 未传角色列表按空列表清空（ADR 0034），仍走同一整体替换入口。
        verify(userRoleService).replaceUserRoles(7L, List.of());
        verify(userRoleService, never()).save(any(SysUserRole.class));
        verify(cacheInvalidator).invalidateUserAfterCommit(7L);
    }

    @Test
    void saveRoleRejectsCustomScopeWithoutDepartmentsBeforeWriting() {
        SysRole role = role(null);
        role.setDataScope(2);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.saveRole(role, List.of()));

        assertEquals(201, error.getCode());
        assertEquals("自定部门不能为空", error.getMsg());
        verify(roleMapper, never()).insert(any(SysRole.class));
        verify(roleDeptMapper, never()).insert(any(SysRoleDept.class));
    }

    @Test
    void saveRoleWritesCustomDepartmentRows() {
        SysRole role = role(null);
        role.setDataScope(2);
        SysDept dept = new SysDept();
        dept.setId(7L);
        when(deptMapper.selectBatchIds(List.of(7L))).thenReturn(List.of(dept));
        when(roleMapper.insert(role)).thenAnswer(invocation -> {
            role.setId(3L);
            return 1;
        });

        service.saveRole(role, List.of(7L, 7L));

        verify(roleDeptMapper).delete(any());
        verify(roleDeptMapper, org.mockito.Mockito.times(1)).insert(any(SysRoleDept.class));
    }

    @Test
    void updateRoleClearsCustomDepartmentsWhenTheScopeIsNotTwo() {
        SysRole existing = role(3L);
        existing.setDataScope(2);
        SysRole update = role(3L);
        update.setDataScope(3);
        when(roleMapper.selectById(3L)).thenReturn(existing);
        when(roleMapper.updateById(update)).thenReturn(1);

        service.updateRole(update, null);

        verify(roleDeptMapper).delete(any());
        verify(roleDeptMapper, never()).insert(any(SysRoleDept.class));
    }

    @Test
    void updateRoleReplacesCustomDepartmentsWhenTheScopeIsTwo() {
        SysRole existing = role(3L);
        existing.setDataScope(2);
        SysRole update = role(3L);
        update.setDataScope(2);
        SysDept first = new SysDept();
        first.setId(7L);
        SysDept second = new SysDept();
        second.setId(8L);
        when(roleMapper.selectById(3L)).thenReturn(existing);
        when(roleMapper.updateById(update)).thenReturn(1);
        when(deptMapper.selectBatchIds(List.of(7L, 8L))).thenReturn(List.of(first, second));

        service.updateRole(update, List.of(7L, 8L));

        verify(roleDeptMapper).delete(any());
        verify(roleDeptMapper, org.mockito.Mockito.times(2)).insert(any(SysRoleDept.class));
    }

    @Test
    void updateRoleKeepsCustomDepartmentsWhenNeitherScopeNorDeptIdsAreSent() {
        SysRole existing = role(3L);
        existing.setDataScope(2);
        SysRole update = role(3L);
        when(roleMapper.selectById(3L)).thenReturn(existing);
        when(roleMapper.updateById(update)).thenReturn(1);

        service.updateRole(update, null);

        verify(roleDeptMapper, never()).delete(any());
    }

    @Test
    void updateRoleRejectsAnUnknownRoleWithANamedMessage() {
        SysRole update = role(3L);
        when(roleMapper.selectById(3L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateRole(update, null));

        assertEquals(201, error.getCode());
        assertEquals("角色不存在", error.getMsg());
        verify(roleMapper, never()).updateById(any(SysRole.class));
    }

    private SysRole role(Long id) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleName("角色" + id);
        return role;
    }
}
