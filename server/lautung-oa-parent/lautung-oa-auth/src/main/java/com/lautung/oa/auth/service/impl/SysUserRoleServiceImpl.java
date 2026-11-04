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
        replace(userId, roleIds, true);
    }

    @Override
    @Transactional
    public void replaceRoleUsers(Long roleId, Collection<Long> userIds) {
        replace(roleId, userIds, false);
    }

    /**
     * 同一张关系表的整体替换：按固定侧删干净后逐行重建。
     * {@code fixedIsUser=true} 表示固定 userId、集合是 roleIds；false 则固定 roleId、集合是 userIds。
     * 两个公共入口保留各自的语义，不把方向判断漏给调用方。
     */
    private void replace(Long fixedId, Collection<Long> otherIds, boolean fixedIsUser) {
        remove(new LambdaQueryWrapper<SysUserRole>()
                .eq(fixedIsUser ? SysUserRole::getUserId : SysUserRole::getRoleId, fixedId));
        for (Long otherId : otherIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(fixedIsUser ? fixedId : otherId);
            relation.setRoleId(fixedIsUser ? otherId : fixedId);
            save(relation);
        }
    }
}
