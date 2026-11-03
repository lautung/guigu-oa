package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.model.system.SysUserRole;
import com.lautung.oa.model.system.SysRoleMenu;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.security.cache.PermissionCacheKey;
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

/**
 * Invalidates the authority JSON cached under {@code perm:cache:<username>}（#237，ADR 0060
 * 的唯一 key 构造点；失效路径不再触碰迁移前的裸 username 键）.
 */
@Service
public class PermissionCacheInvalidator {

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

    /**
     * 仍在该角色里的成员（#146）。删除角色时关系行会先被清掉，调用方必须在删行之前
     * 用本方法枚举成员，删行后再交给 {@link #invalidateUsersAfterCommit}。
     */
    public Set<Long> membersOfRole(Long roleId) {
        if (roleId == null) {
            return Set.of();
        }
        return userIdsInRole(roleId);
    }

    public void invalidateMenuMembersAfterCommit(Long menuId) {
        if (menuId == null) {
            return;
        }
        Set<Long> userIds = new LinkedHashSet<>();
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getMenuId, menuId);
        for (SysRoleMenu roleMenu : sysRoleMenuService.list(wrapper)) {
            if (roleMenu.getRoleId() != null) {
                userIds.addAll(userIdsInRole(roleMenu.getRoleId()));
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

    public void invalidateUsersAfterCommit(Collection<Long> userIds) {
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

        Runnable deleteCachedAuthorities = () -> usernames.forEach(username ->
                redisTemplate.delete(PermissionCacheKey.of(username)));
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
