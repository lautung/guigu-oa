package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.mapper.SysDeptMapper;
import com.lautung.oa.auth.mapper.SysRoleDeptMapper;
import com.lautung.oa.auth.mapper.SysRoleMapper;
import com.lautung.oa.auth.service.SysRoleMenuService;
import com.lautung.oa.auth.service.SysRoleService;
import com.lautung.oa.auth.service.SysUserRoleService;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.auth.service.PermissionCacheInvalidator;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.SysDept;
import com.lautung.oa.model.system.SysRole;
import com.lautung.oa.model.system.SysRoleDept;
import com.lautung.oa.model.system.SysRoleMenu;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.model.system.SysUserRole;
import com.lautung.oa.vo.system.AssginRoleVo;
import com.lautung.oa.vo.system.SysDeptOptionVo;
import com.lautung.oa.vo.system.SysRoleDetailVo;
import com.lautung.oa.vo.system.SysRoleUserVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private static final int CUSTOM_DEPARTMENTS = 2;

    @Autowired
    private SysUserRoleService sysUserRoleService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysRoleMenuService sysRoleMenuService;

    @Autowired
    private SysRoleDeptMapper sysRoleDeptMapper;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private PermissionCacheInvalidator permissionCacheInvalidator;

    @Override
    @Transactional
    public boolean updateById(SysRole role) {
        boolean updated = baseMapper.updateById(role) > 0;
        if (updated) {
            permissionCacheInvalidator.invalidateRoleMembersAfterCommit(role.getId());
        }
        return updated;
    }

    @Override
    @Transactional
    public boolean removeById(Serializable roleId) {
        Long id = Long.valueOf(roleId.toString());
        // 成员必须在关系行被清掉之前枚举（#146）：删行后 list 读不到，提交后的缓存失效就清不到人。
        Set<Long> members = permissionCacheInvalidator.membersOfRole(id);
        boolean removed = baseMapper.deleteById(roleId) > 0;
        if (removed) {
            deleteRoleRelations(id);
            permissionCacheInvalidator.invalidateUsersAfterCommit(members);
        }
        return removed;
    }

    @Override
    public boolean removeById(SysRole role) {
        return role != null && removeById(role.getId());
    }

    @Override
    @Transactional
    public boolean removeByIds(Collection<?> roleIds) {
        // 空列表（含全 null 元素）是明确拒绝而非幂等无操作，与单删删不到行同为 201 家族（#156，ADR 0040）。
        List<Long> ids = roleIds == null ? List.of() : roleIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(roleId -> Long.valueOf(roleId.toString()))
                .toList();
        if (ids.isEmpty()) {
            throw new BusinessException(201, "删除列表不能为空");
        }
        // 同 removeById：成员枚举先于关系行删除（#146）。
        java.util.Set<Long> members = new java.util.LinkedHashSet<>();
        ids.forEach(id -> members.addAll(permissionCacheInvalidator.membersOfRole(id)));
        boolean removed = baseMapper.deleteByIds(ids) > 0;
        if (removed) {
            ids.forEach(this::deleteRoleRelations);
            permissionCacheInvalidator.invalidateUsersAfterCommit(members);
        }
        return removed;
    }

    /** 删角色时同步回收授予关系行：用户-角色、角色-菜单与自定部门（#146/#225，逻辑删除）。 */
    private void deleteRoleRelations(Long roleId) {
        LambdaQueryWrapper<SysUserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(SysUserRole::getRoleId, roleId);
        sysUserRoleService.remove(userRoleWrapper);

        LambdaQueryWrapper<SysRoleMenu> roleMenuWrapper = new LambdaQueryWrapper<>();
        roleMenuWrapper.eq(SysRoleMenu::getRoleId, roleId);
        sysRoleMenuService.remove(roleMenuWrapper);

        clearDeptRows(roleId);
    }
    
    //1 查询所有角色 和 当前用户所属角色
    @Override
    public Map<String, Object> findRoleDataByUserId(Long userId) {
        
        //1 查询所有角色，返回list集合，返回
        List<SysRole> allRoleList = 
                baseMapper.selectList(null);

        //2 根据userid查询 角色用户关系表，查询userid对应所有角色id
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId,userId);
        List<SysUserRole> existUserRoleList = sysUserRoleService.list(wrapper);

        //从查询出来的用户id对应角色list集合，获取所有角色id
//        List<Long> list = new ArrayList<>();
//        for (SysUserRole sysUserRole:existUserRoleList) {
//            Long roleId = sysUserRole.getRoleId();
//            list.add(roleId);
//        }
        List<Long> existRoleIdList =
                existUserRoleList.stream().map(c -> c.getRoleId()).collect(Collectors.toList());

        //3 根据查询所有角色id，找到对应角色信息
        //根据角色id到所有的角色的list集合进行比较
        List<SysRole> assignRoleList = new ArrayList<>();
        for(SysRole sysRole : allRoleList) {
            //比较
            if(existRoleIdList.contains(sysRole.getId())) {
                assignRoleList.add(sysRole);
            }
        }
        
        //4 把得到两部分数据封装map集合，返回
        Map<String, Object> roleMap = new HashMap<>();
        roleMap.put("assginRoleList", assignRoleList);
        roleMap.put("allRolesList", allRoleList);
        return roleMap;
    }

    //2 为用户分配角色
    @Override
    @Transactional
    public void doAssign(AssginRoleVo assginRoleVo) {
        //校验必须在删除既有分配之前：任何非法 id 都拒绝整个分配，不改动用户既有角色（#144）。
        //已删除角色走 @TableLogic 逻辑删除，selectById 读不到，与不存在的 id 一并拒绝。
        List<Long> requestedRoleIds = assginRoleVo.getRoleIdList();
        if (requestedRoleIds != null) {
            for (Long roleId : requestedRoleIds) {
                if (roleId == null) {
                    continue;
                }
                if (baseMapper.selectById(roleId) == null) {
                    throw new BusinessException(201, "只能分配已存在的角色");
                }
            }
        }

        // 整体替换走 sys_user_role 的唯一写入口；重复 id 去重，空列表是合法的清空。
        List<Long> roleIdList = requestedRoleIds == null
                ? List.of()
                : requestedRoleIds.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                        .stream()
                        .toList();
        sysUserRoleService.replaceUserRoles(assginRoleVo.getUserId(), roleIdList);
        permissionCacheInvalidator.invalidateUserAfterCommit(assginRoleVo.getUserId());
    }

    @Override
    public IPage<SysRoleUserVo> findRoleUsers(Long roleId, String keyword, Boolean assigned,
                                              Long page, Long limit) {
        if (roleId == null || getById(roleId) == null) {
            throw new BusinessException(201, "角色不存在");
        }
        Page<SysUser> pageParam = PageWindow.of(page, limit);
        // 成员集合先读出来：@TableLogic 由实体查询带出 is_deleted = 0，service 不再拼存活谓词。
        Set<Long> memberIds = memberIdsOf(roleId);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            // 角色成员自己的关键字口径：用户名 / 姓名 / 手机号任一命中；用户页只 like username，不复用该口径。
            wrapper.and(nested -> nested.like(SysUser::getUsername, trimmed)
                    .or().like(SysUser::getName, trimmed)
                    .or().like(SysUser::getPhone, trimmed));
        }
        // 角色是全局配置：成员列表不套数据范围（ADR 0056 的例外由本端点自己声明）。
        if (Boolean.TRUE.equals(assigned)) {
            if (memberIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(SysUser::getId, memberIds);
            }
        } else if (Boolean.FALSE.equals(assigned) && !memberIds.isEmpty()) {
            wrapper.notIn(SysUser::getId, memberIds);
        }
        // 稳定排序（ADR 0045）：分页翻页不因同值行漂移。
        wrapper.orderByAsc(SysUser::getId);

        IPage<SysUser> users = sysUserService.page(pageParam, wrapper);
        Page<SysRoleUserVo> result = new Page<>(users.getCurrent(), users.getSize(), users.getTotal());
        result.setRecords(users.getRecords().stream()
                .map(user -> toRoleUserVo(user,
                        assigned != null ? assigned : memberIds.contains(user.getId())))
                .toList());
        return result;
    }

    @Override
    @Transactional
    public void assignUsers(Long roleId, List<Long> userIds) {
        if (roleId == null || getById(roleId) == null) {
            throw new BusinessException(201, "角色不存在");
        }
        // 校验必须在删除既有成员之前：任何非法 id 都拒绝整个分配，不改动角色成员（#144 同一纪律）。
        List<Long> requested = userIds == null
                ? List.of()
                : userIds.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                        .stream()
                        .toList();
        if (!requested.isEmpty()) {
            List<Long> existing = sysUserService.listByIds(requested).stream()
                    .map(SysUser::getId)
                    .toList();
            if (existing.size() != requested.size()) {
                throw new BusinessException(201, "只能分配已存在的用户");
            }
        }

        // 移出与加入的成员权限都可能变化，两边都在关系行变化前/后收集，提交后统一失效。
        Set<Long> affected = new LinkedHashSet<>(permissionCacheInvalidator.membersOfRole(roleId));
        sysUserRoleService.replaceRoleUsers(roleId, requested);
        affected.addAll(requested);
        permissionCacheInvalidator.invalidateUsersAfterCommit(affected);
    }

    private Set<Long> memberIdsOf(Long roleId) {
        return sysUserRoleService.list(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId))
                .stream()
                .map(SysUserRole::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private SysRoleUserVo toRoleUserVo(SysUser user, boolean assigned) {
        SysRoleUserVo vo = new SysRoleUserVo();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setPhone(user.getPhone());
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setAssigned(assigned);
        return vo;
    }

    @Override
    public List<String> findRoleCodesByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Long> roleIds = sysUserRoleService.list(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds))
                .stream()
                .map(SysRole::getRoleCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    @Override
    @Transactional
    public void saveRole(SysRole role, List<Long> deptIds) {
        int dataScope = scopeOf(role);
        role.setDataScope(dataScope);
        // 先校验部门再写角色：非法部门不留下半个角色（同一事务里的明确拒绝）。
        List<Long> normalizedDeptIds = dataScope == CUSTOM_DEPARTMENTS
                ? validateDeptIds(deptIds)
                : List.of();
        if (dataScope == CUSTOM_DEPARTMENTS && normalizedDeptIds.isEmpty()) {
            throw new BusinessException(201, "自定部门不能为空");
        }
        if (!save(role)) {
            throw new BusinessException(201, "角色保存失败");
        }
        if (dataScope == CUSTOM_DEPARTMENTS) {
            replaceDeptRows(role.getId(), normalizedDeptIds);
        }
    }

    @Override
    @Transactional
    public void updateRole(SysRole role, List<Long> deptIds) {
        SysRole existing = role.getId() == null ? null : getById(role.getId());
        if (existing == null) {
            throw new BusinessException(201, "角色不存在");
        }
        int dataScope = role.getDataScope() != null ? role.getDataScope() : scopeOf(existing);
        // deptIds 为空表示不覆盖（ADR 0034）；非 2 档时即使提交部门也要按最终范围清空旧行。
        List<Long> normalizedDeptIds = deptIds != null && dataScope == CUSTOM_DEPARTMENTS
                ? validateDeptIds(deptIds)
                : null;
        if (!updateById(role)) {
            throw new BusinessException(201, "角色更新失败");
        }
        if (dataScope != CUSTOM_DEPARTMENTS) {
            clearDeptRows(role.getId());
        } else if (normalizedDeptIds != null) {
            replaceDeptRows(role.getId(), normalizedDeptIds);
        }
    }

    @Override
    public SysRoleDetailVo findDetail(Long id) {
        SysRole role = id == null ? null : getById(id);
        if (role == null) {
            return null;
        }
        SysRoleDetailVo detail = new SysRoleDetailVo();
        detail.setId(role.getId());
        detail.setRoleName(role.getRoleName());
        detail.setRoleCode(role.getRoleCode());
        detail.setDescription(role.getDescription());
        detail.setDataScope(role.getDataScope());
        detail.setDeptIds(findingDeptIds(role.getId()));
        detail.setCreateTime(role.getCreateTime());
        detail.setUpdateTime(role.getUpdateTime());
        return detail;
    }

    @Override
    public List<SysDeptOptionVo> findDeptOptions() {
        return sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getStatus, 1)
                        .orderByAsc(SysDept::getParentId)
                        .orderByAsc(SysDept::getSortValue)
                        .orderByAsc(SysDept::getId))
                .stream()
                .map(dept -> {
                    SysDeptOptionVo option = new SysDeptOptionVo();
                    option.setId(dept.getId());
                    option.setName(dept.getName());
                    option.setParentId(dept.getParentId());
                    return option;
                })
                .toList();
    }

    /** 只接受存在且未删除的部门；去重后保持提交顺序。 */
    private List<Long> validateDeptIds(List<Long> deptIds) {
        if (deptIds == null) {
            return List.of();
        }
        List<Long> distinct = deptIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<Long> existing = sysDeptMapper.selectBatchIds(distinct).stream()
                .map(SysDept::getId)
                .toList();
        if (existing.size() != distinct.size()) {
            throw new BusinessException(201, "只能选择已存在的部门");
        }
        return distinct;
    }

    private List<Long> findingDeptIds(Long roleId) {
        return sysRoleDeptMapper.selectList(new LambdaQueryWrapper<SysRoleDept>()
                        .eq(SysRoleDept::getRoleId, roleId)
                        .orderByAsc(SysRoleDept::getId))
                .stream()
                .map(SysRoleDept::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void replaceDeptRows(Long roleId, List<Long> deptIds) {
        clearDeptRows(roleId);
        for (Long deptId : deptIds) {
            SysRoleDept relation = new SysRoleDept();
            relation.setRoleId(roleId);
            relation.setDeptId(deptId);
            sysRoleDeptMapper.insert(relation);
        }
    }

    private void clearDeptRows(Long roleId) {
        sysRoleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>()
                .eq(SysRoleDept::getRoleId, roleId));
    }

    /** 未设置 data_scope 的老角色按 5（仅本人）理解，与 DataScopeResolver 同一口径。 */
    private int scopeOf(SysRole role) {
        return role.getDataScope() == null ? 5 : role.getDataScope();
    }

//    @Autowired
//    private SysRoleMapper sysRoleMapper;

}
