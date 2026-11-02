package com.lautung.oa.auth.datascope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.auth.mapper.SysDeptMapper;
import com.lautung.oa.auth.mapper.SysRoleDeptMapper;
import com.lautung.oa.auth.mapper.SysRoleMapper;
import com.lautung.oa.auth.mapper.SysUserMapper;
import com.lautung.oa.auth.mapper.SysUserRoleMapper;
import com.lautung.oa.model.system.SysDept;
import com.lautung.oa.model.system.SysRole;
import com.lautung.oa.model.system.SysRoleDept;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.model.system.SysUserRole;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据范围解析器（ADR 0053/0057）：把用户角色上的五档 {@code sys_role.data_scope} 解析成
 * {@link DataScope}，每个请求解析一次，不缓存。
 *
 * <p>五档：{@code 1} 全部、{@code 2} 自定部门（读 {@code sys_role_dept}）、{@code 3} 本部门、
 * {@code 4} 本部门及以下（{@code ancestors LIKE '%,<本部门 id>,%'}，含自身）、{@code 5} 仅本人。
 * 多角色取并集：任一角色 {@code 1} → {@code unrestricted}；{@code 2/3/4} 的部门并进部门集；
 * 任一角色 {@code 5} → 设本人行。没有角色、或只有 {@code 3/4} 但本人无部门，得到空集 → 零行。
 *
 * <p>只返回值对象，不猜业务列：部门列与本人列由四个接入查询各自回答。
 */
@Component
public class DataScopeResolver {

    private static final int ALL = 1;
    private static final int CUSTOM_DEPARTMENTS = 2;
    private static final int OWN_DEPARTMENT = 3;
    private static final int OWN_DEPARTMENT_AND_BELOW = 4;
    private static final int SELF_ONLY = 5;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysDeptMapper deptMapper;

    public DataScopeResolver(SysUserMapper userMapper,
                             SysUserRoleMapper userRoleMapper,
                             SysRoleMapper roleMapper,
                             SysRoleDeptMapper roleDeptMapper,
                             SysDeptMapper deptMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.deptMapper = deptMapper;
    }

    /**
     * 解析该用户可见范围。未认证（{@code userId == null}）按零行处理，调用方仍需先过认证与 gate。
     */
    public DataScope resolve(Long userId) {
        if (userId == null) {
            return DataScope.of(Set.of(), null);
        }

        List<Long> roleIds = rolesOf(userId);
        if (roleIds.isEmpty()) {
            return DataScope.of(Set.of(), null);
        }
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds));
        if (roles.isEmpty()) {
            return DataScope.of(Set.of(), null);
        }
        if (roles.stream().anyMatch(role -> scopeOf(role) == ALL)) {
            return DataScope.unrestricted();
        }

        Set<Long> deptIds = new LinkedHashSet<>();
        deptIds.addAll(customDepartments(roles));
        deptIds.addAll(ownDepartment(roles, userId));

        boolean selfOnly = roles.stream().anyMatch(role -> scopeOf(role) == SELF_ONLY);
        return DataScope.of(deptIds, selfOnly ? userId : null);
    }

    private List<Long> rolesOf(Long userId) {
        return userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** 自定部门（档位 2）：只认 {@code sys_role_dept}，且部门必须仍在当前树里。 */
    private Set<Long> customDepartments(List<SysRole> roles) {
        List<Long> customRoleIds = roles.stream()
                .filter(role -> scopeOf(role) == CUSTOM_DEPARTMENTS)
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (customRoleIds.isEmpty()) {
            return Set.of();
        }
        List<Long> requested = roleDeptMapper.selectList(
                        new LambdaQueryWrapper<SysRoleDept>().in(SysRoleDept::getRoleId, customRoleIds))
                .stream()
                .map(SysRoleDept::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (requested.isEmpty()) {
            return Set.of();
        }
        // 软删部门不进入可见集：按当前树解析，不做历史快照。
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>().in(SysDept::getId, requested))
                .stream()
                .map(SysDept::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 本部门（档位 3）与本部门及以下（档位 4）；本人无部门或部门已删则不产出。 */
    private Set<Long> ownDepartment(List<SysRole> roles, Long userId) {
        boolean own = roles.stream().anyMatch(role -> scopeOf(role) == OWN_DEPARTMENT);
        boolean below = roles.stream().anyMatch(role -> scopeOf(role) == OWN_DEPARTMENT_AND_BELOW);
        if (!own && !below) {
            return Set.of();
        }
        SysUser user = userMapper.selectById(userId);
        Long deptId = user == null ? null : user.getDeptId();
        if (deptId == null || deptMapper.selectById(deptId) == null) {
            return Set.of();
        }
        Set<Long> deptIds = new LinkedHashSet<>();
        deptIds.add(deptId);
        if (below) {
            deptIds.addAll(deptMapper.selectSubtreeIds(deptId));
        }
        return deptIds;
    }

    /** 老角色没有 data_scope 时按列默认值 5（仅本人）理解，保持受限而不是意外放开。 */
    private int scopeOf(SysRole role) {
        return role.getDataScope() == null ? SELF_ONLY : role.getDataScope();
    }
}
