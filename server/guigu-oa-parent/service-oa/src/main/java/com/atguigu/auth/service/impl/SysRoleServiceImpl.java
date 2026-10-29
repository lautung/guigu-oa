package com.atguigu.auth.service.impl;

import com.atguigu.auth.mapper.SysRoleMapper;
import com.atguigu.auth.service.SysRoleMenuService;
import com.atguigu.auth.service.SysRoleService;
import com.atguigu.auth.service.SysUserRoleService;
import com.atguigu.auth.service.PermissionCacheInvalidator;
import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.model.system.SysRole;
import com.atguigu.model.system.SysRoleMenu;
import com.atguigu.model.system.SysUserRole;
import com.atguigu.vo.system.AssginRoleVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {
    
    @Autowired
    private SysUserRoleService sysUserRoleService;

    @Autowired
    private SysRoleMenuService sysRoleMenuService;

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
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        List<Long> ids = roleIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(roleId -> Long.valueOf(roleId.toString()))
                .toList();
        // 同 removeById：成员枚举先于关系行删除（#146）。
        java.util.Set<Long> members = new java.util.LinkedHashSet<>();
        ids.forEach(id -> members.addAll(permissionCacheInvalidator.membersOfRole(id)));
        boolean removed = !ids.isEmpty() && baseMapper.deleteByIds(ids) > 0;
        if (removed) {
            ids.forEach(this::deleteRoleRelations);
            permissionCacheInvalidator.invalidateUsersAfterCommit(members);
        }
        return removed;
    }

    /** 删角色时同步回收授予关系行：用户-角色与角色-菜单（#146，逻辑删除）。 */
    private void deleteRoleRelations(Long roleId) {
        LambdaQueryWrapper<SysUserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(SysUserRole::getRoleId, roleId);
        sysUserRoleService.remove(userRoleWrapper);

        LambdaQueryWrapper<SysRoleMenu> roleMenuWrapper = new LambdaQueryWrapper<>();
        roleMenuWrapper.eq(SysRoleMenu::getRoleId, roleId);
        sysRoleMenuService.remove(roleMenuWrapper);
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
                    throw new GuiguException(201, "只能分配已存在的角色");
                }
            }
        }

        //把用户之前分配角色数据删除，用户角色关系表里面，根据userid删除
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId,assginRoleVo.getUserId());
        sysUserRoleService.remove(wrapper);

        //重新进行分配
        List<Long> roleIdList = requestedRoleIds == null ? List.of() : requestedRoleIds;
        for(Long roleId:roleIdList) {
            if(StringUtils.isEmpty(roleId)) {
                continue;
            }
            SysUserRole sysUserRole = new SysUserRole();
            sysUserRole.setUserId(assginRoleVo.getUserId());
            sysUserRole.setRoleId(roleId);
            sysUserRoleService.save(sysUserRole);
        }
        permissionCacheInvalidator.invalidateUserAfterCommit(assginRoleVo.getUserId());
    }

//    @Autowired
//    private SysRoleMapper sysRoleMapper;

}
