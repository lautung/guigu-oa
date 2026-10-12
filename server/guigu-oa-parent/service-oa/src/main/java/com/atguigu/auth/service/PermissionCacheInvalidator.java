package com.atguigu.auth.service;

import com.atguigu.model.system.SysUser;
import com.atguigu.model.system.SysUserRole;
import com.atguigu.model.system.SysRoleMenu;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Invalidates the authority JSON cached by username in {@code IndexController}. */
@Service
public class PermissionCacheInvalidator {

    private static final Long SEED_ADMIN_USER_ID = 1L;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysUserRoleService sysUserRoleService;

    @Autowired
    private SysRoleMenuService sysRoleMenuService;

    public void invalidateUserAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        invalidateUsersAfterCommit(List.of(userId));
    }

    public void invalidateRoleMembersAfterCommit(Long roleId) {
        if (roleId == null) {
            return;
        }
        invalidateUsersAfterCommit(userIdsInRole(roleId));
    }

    public void invalidateMenuMembersAndAdminAfterCommit(Long menuId) {
        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(SEED_ADMIN_USER_ID);
        if (menuId != null) {
            LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysRoleMenu::getMenuId, menuId);
            for (SysRoleMenu roleMenu : sysRoleMenuService.list(wrapper)) {
                if (roleMenu.getRoleId() != null) {
                    userIds.addAll(userIdsInRole(roleMenu.getRoleId()));
                }
            }
        }
        invalidateUsersAfterCommit(userIds);
    }

    private Set<Long> userIdsInRole(Long roleId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getRoleId, roleId);
        Set<Long> userIds = new LinkedHashSet<>();
        for (SysUserRole userRole : sysUserRoleService.list(wrapper)) {
            if (userRole.getUserId() != null) {
                userIds.add(userRole.getUserId());
            }
        }
        return userIds;
    }

    private void invalidateUsersAfterCommit(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        List<String> usernames = sysUserService.listByIds(userIds).stream()
                .map(SysUser::getUsername)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (usernames.isEmpty()) {
            return;
        }

        Runnable deleteCachedAuthorities = () -> usernames.forEach(redisTemplate::delete);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteCachedAuthorities.run();
                }
            });
        } else {
            deleteCachedAuthorities.run();
        }
    }
}
