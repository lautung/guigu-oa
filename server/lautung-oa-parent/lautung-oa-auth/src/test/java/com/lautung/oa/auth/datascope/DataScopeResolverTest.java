package com.lautung.oa.auth.datascope;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lautung.oa.auth.mapper.SysDeptMapper;
import com.lautung.oa.auth.mapper.SysRoleDeptMapper;
import com.lautung.oa.auth.mapper.SysRoleMapper;
import com.lautung.oa.auth.mapper.SysUserMapper;
import com.lautung.oa.auth.mapper.SysUserRoleMapper;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.model.system.SysDept;
import com.lautung.oa.model.system.SysRole;
import com.lautung.oa.model.system.SysRoleDept;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.model.system.SysUserRole;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 五档解析与多角色并集（ADR 0057）：1 全部、2 自定部门、3 本部门、4 本部门及以下、5 仅本人；
 * 任一角色 1 直接全部；部门集与本人可同时存在；解析不出任何可见行时是零行。
 */
class DataScopeResolverTest {

    private static final long USER_ID = 7L;

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysRoleDeptMapper roleDeptMapper = mock(SysRoleDeptMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final DataScopeResolver resolver = new DataScopeResolver(
            userMapper, userRoleMapper, roleMapper, roleDeptMapper, deptMapper);

    @BeforeAll
    static void initTableMetadata() {
        // Lambda wrapper 需要实体的列元数据，纯单元测试里手工初始化（testing.md）。
        List.of(SysUser.class, SysUserRole.class, SysRole.class, SysRoleDept.class, SysDept.class)
                .forEach(entity -> TableInfoHelper.initTableInfo(
                        new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity));
    }

    @Test
    void userWithoutRolesSeesNoRows() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        DataScope scope = resolver.resolve(USER_ID);

        assertZeroRows(scope);
        verify(roleMapper, never()).selectList(any());
    }

    @Test
    void missingUserSeesNoRows() {
        assertZeroRows(resolver.resolve(null));
        verify(userRoleMapper, never()).selectList(any());
    }

    @Test
    void anyUnrestrictedRoleWinsTheUnion() {
        userHasRoles(3L, 1L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(3, 5), role(1, 1)));

        assertTrue(resolver.resolve(USER_ID).isUnrestricted());
        verify(roleDeptMapper, never()).selectList(any());
        verify(userMapper, never()).selectById(USER_ID);
    }

    @Test
    void ownDepartmentScopeUsesTheUsersDepartment() {
        userHasRoles(3L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(3, 3)));
        userInDepartment(10L);
        departmentExists(10L);

        DataScope scope = resolver.resolve(USER_ID);

        assertFalse(scope.isUnrestricted());
        assertEquals(Set.of(10L), scope.getDeptIds());
        assertNull(scope.getSelfUserId());
    }

    @Test
    void ownDepartmentAndBelowIncludesTheWholeSubtree() {
        userHasRoles(4L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(4, 4)));
        userInDepartment(10L);
        departmentExists(10L);
        when(deptMapper.selectSubtreeIds(10L)).thenReturn(List.of(10L, 11L, 12L));

        DataScope scope = resolver.resolve(USER_ID);

        assertEquals(Set.of(10L, 11L, 12L), scope.getDeptIds());
        assertNull(scope.getSelfUserId());
    }

    @Test
    void customDepartmentScopeComesFromRoleDeptAndDropsDeletedDepartments() {
        userHasRoles(2L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(2, 2)));
        roleDepts(2L, 20L, 21L);
        existingDepartments(20L);

        DataScope scope = resolver.resolve(USER_ID);

        assertEquals(Set.of(20L), scope.getDeptIds());
        assertNull(scope.getSelfUserId());
    }

    @Test
    void selfOnlyScopeCarriesTheUserRow() {
        userHasRoles(5L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(5, 5)));

        DataScope scope = resolver.resolve(USER_ID);

        assertTrue(scope.getDeptIds().isEmpty());
        assertEquals(USER_ID, scope.getSelfUserId());
    }

    @Test
    void ownDepartmentAndSelfOnlyRolesUnionBothParts() {
        userHasRoles(3L, 5L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(3, 3), role(5, 5)));
        userInDepartment(10L);
        departmentExists(10L);

        DataScope scope = resolver.resolve(USER_ID);

        assertEquals(Set.of(10L), scope.getDeptIds());
        assertEquals(USER_ID, scope.getSelfUserId());
    }

    @Test
    void customDepartmentsAndSelfOnlyRolesUnionBothParts() {
        userHasRoles(2L, 5L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(2, 2), role(5, 5)));
        roleDepts(2L, 20L);
        existingDepartments(20L);

        DataScope scope = resolver.resolve(USER_ID);

        assertEquals(Set.of(20L), scope.getDeptIds());
        assertEquals(USER_ID, scope.getSelfUserId());
    }

    @Test
    void ownDepartmentAndBelowUnionsWithAnotherRolesOwnDepartment() {
        userHasRoles(3L, 4L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(3, 3), role(4, 4)));
        userInDepartment(10L);
        departmentExists(10L);
        when(deptMapper.selectSubtreeIds(10L)).thenReturn(List.of(10L, 11L));

        assertEquals(Set.of(10L, 11L), resolver.resolve(USER_ID).getDeptIds());
    }

    @Test
    void ownDepartmentScopeWithoutAUserDepartmentSeesNoRows() {
        userHasRoles(3L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(3, 3)));
        userInDepartment(null);

        assertZeroRows(resolver.resolve(USER_ID));
    }

    @Test
    void ownDepartmentScopeWithADeletedDepartmentSeesNoRows() {
        userHasRoles(3L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(3, 3)));
        userInDepartment(10L);

        assertZeroRows(resolver.resolve(USER_ID));
    }

    @Test
    void customScopeWhoseDepartmentsAreAllDeletedSeesNoRows() {
        userHasRoles(2L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(2, 2)));
        roleDepts(2L, 20L);
        existingDepartments();

        assertZeroRows(resolver.resolve(USER_ID));
    }

    @Test
    void roleWithoutADataScopeDefaultsToSelfOnly() {
        userHasRoles(9L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role(9, null)));

        DataScope scope = resolver.resolve(USER_ID);

        assertTrue(scope.getDeptIds().isEmpty());
        assertEquals(USER_ID, scope.getSelfUserId());
    }

    private void assertZeroRows(DataScope scope) {
        assertFalse(scope.isUnrestricted());
        assertTrue(scope.getDeptIds().isEmpty());
        assertNull(scope.getSelfUserId());
    }

    private void userHasRoles(long... roleIds) {
        List<SysUserRole> links = Arrays.stream(roleIds).mapToObj(roleId -> {
            SysUserRole link = new SysUserRole();
            link.setUserId(USER_ID);
            link.setRoleId(roleId);
            return link;
        }).toList();
        when(userRoleMapper.selectList(any())).thenReturn(links);
    }

    private void userInDepartment(Long deptId) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setDeptId(deptId);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
    }

    private void departmentExists(Long deptId) {
        SysDept dept = new SysDept();
        dept.setId(deptId);
        when(deptMapper.selectById(deptId)).thenReturn(dept);
    }

    private void roleDepts(long roleId, Long... deptIds) {
        List<SysRoleDept> links = Arrays.stream(deptIds).map(deptId -> {
            SysRoleDept link = new SysRoleDept();
            link.setRoleId(roleId);
            link.setDeptId(deptId);
            return link;
        }).toList();
        when(roleDeptMapper.selectList(any())).thenReturn(links);
    }

    private void existingDepartments(Long... deptIds) {
        List<SysDept> depts = Arrays.stream(deptIds).map(deptId -> {
            SysDept dept = new SysDept();
            dept.setId(deptId);
            return dept;
        }).toList();
        when(deptMapper.selectList(any())).thenReturn(depts);
    }

    private static SysRole role(long id, Integer dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setDataScope(dataScope);
        return role;
    }
}
