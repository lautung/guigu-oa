package com.lautung.oa.auth.service.impl;

import com.lautung.oa.model.system.SysUserRole;
import com.lautung.oa.auth.mapper.SysUserRoleMapper;
import com.lautung.oa.auth.service.SysUserRoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * <p>
 * 用户角色 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-02
 */
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {

    @Override
    @Transactional
    public void replaceUserRoles(Long userId, Collection<Long> roleIds) {
        remove(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            save(relation);
        }
    }

    @Override
    @Transactional
    public void replaceRoleUsers(Long roleId, Collection<Long> userIds) {
        remove(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
        for (Long userId : userIds) {
            SysUserRole relation = new SysUserRole();
            relation.setRoleId(roleId);
            relation.setUserId(userId);
            save(relation);
        }
    }
}
