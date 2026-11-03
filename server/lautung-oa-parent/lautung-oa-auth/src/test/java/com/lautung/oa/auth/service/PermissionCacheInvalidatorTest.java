package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 权限缓存失效只落在加前缀的键上（#237，ADR 0060）：唯一 key 构造点
 * {@code perm:cache:<username>}，失效路径不再删裸 username 键。
 */
class PermissionCacheInvalidatorTest {

    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    private final SysUserService sysUserService = mock(SysUserService.class);
    private final SysUserRoleService sysUserRoleService = mock(SysUserRoleService.class);
    private final SysRoleMenuService sysRoleMenuService = mock(SysRoleMenuService.class);

    private PermissionCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        invalidator = new PermissionCacheInvalidator();
        ReflectionTestUtils.setField(invalidator, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(invalidator, "sysUserService", sysUserService);
        ReflectionTestUtils.setField(invalidator, "sysUserRoleService", sysUserRoleService);
        ReflectionTestUtils.setField(invalidator, "sysRoleMenuService", sysRoleMenuService);
    }

    @Test
    void invalidationDeletesOnlyThePrefixedCacheKey() {
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        when(sysUserService.listByIds(List.of(1L))).thenReturn(List.of(admin));

        invalidator.invalidateUsersAfterCommit(List.of(1L));

        verify(redisTemplate).delete("perm:cache:admin");
        verify(redisTemplate, never()).delete("admin");
    }

    @Test
    void emptyInputAndBlankUsernamesNeverTouchRedis() {
        invalidator.invalidateUsersAfterCommit(List.of());
        invalidator.invalidateUserAfterCommit(null);

        SysUser blank = new SysUser();
        blank.setId(2L);
        blank.setUsername(" ");
        when(sysUserService.listByIds(List.of(2L))).thenReturn(List.of(blank));
        invalidator.invalidateUsersAfterCommit(List.of(2L));

        verifyNoInteractions(redisTemplate);
    }
}
