package com.atguigu.auth;

import com.atguigu.auth.service.SysRoleService;
import com.atguigu.auth.service.SysMenuService;
import com.atguigu.auth.service.SysUserRoleService;
import com.atguigu.model.system.SysMenu;
import com.atguigu.model.system.SysRole;
import com.atguigu.model.system.SysUserRole;
import com.atguigu.vo.system.AssginMenuVo;
import com.atguigu.vo.system.AssginRoleVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/authority-cache-contract-schema.sql"})
class PermissionCacheInvalidationTest {

    @Autowired
    private SysRoleService sysRoleService;

    @Autowired
    private SysMenuService sysMenuService;

    @Autowired
    private SysUserRoleService sysUserRoleService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ConcurrentMap<String, String> permissionCache;

    @Test
    void changingUsersRolesImmediatelyRemovesOnlyThatUsersCachedPermissionJson() {
        permissionCache.put("alice", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("bob", "[{\"authority\":\"bnt.approval.view\"}]");

        AssginRoleVo assignment = new AssginRoleVo();
        assignment.setUserId(2L);
        assignment.setRoleIdList(List.of(11L));
        sysRoleService.doAssign(assignment);

        assertFalse(permissionCache.containsKey("alice"), "被改角色的用户不能继续使用旧权限 JSON");
        assertTrue(permissionCache.containsKey("bob"), "未改角色的用户缓存应保留");
    }

    @Test
    void changingMenusForARoleImmediatelyRemovesPermissionJsonForEveryMember() {
        permissionCache.put("alice", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("bob", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("carol", "[{\"authority\":\"bnt.manage.view\"}]");

        AssginMenuVo assignment = new AssginMenuVo();
        assignment.setRoleId(10L);
        assignment.setMenuIdList(List.of(21L));
        sysMenuService.doAssign(assignment);

        assertFalse(permissionCache.containsKey("alice"), "角色菜单变化后应清除第一位成员的旧权限");
        assertFalse(permissionCache.containsKey("bob"), "角色菜单变化后应清除第二位成员的旧权限");
        assertTrue(permissionCache.containsKey("carol"), "无关角色成员缓存应保留");
    }

    @Test
    void changingRoleDetailsRemovesCachedPermissionJsonForEveryMember() {
        permissionCache.put("alice", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("bob", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("carol", "[{\"authority\":\"bnt.manage.view\"}]");

        SysRole role = sysRoleService.getById(10L);
        role.setRoleName("已更新审批人");
        assertTrue(sysRoleService.updateById(role));

        assertFalse(permissionCache.containsKey("alice"));
        assertFalse(permissionCache.containsKey("bob"));
        assertTrue(permissionCache.containsKey("carol"));
    }

    @Test
    void deletingRoleRemovesCachedPermissionJsonForItsMembers() {
        permissionCache.put("alice", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("bob", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("carol", "[{\"authority\":\"bnt.manage.view\"}]");

        assertTrue(sysRoleService.removeById(10L));

        assertFalse(permissionCache.containsKey("alice"));
        assertFalse(permissionCache.containsKey("bob"));
        assertTrue(permissionCache.containsKey("carol"));
    }

    @Test
    void changingMenuDetailsRemovesCachedPermissionJsonForMembersAndAdmin() {
        permissionCache.put("admin", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("alice", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("bob", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("carol", "[{\"authority\":\"bnt.manage.view\"}]");

        SysMenu menu = sysMenuService.getById(20L);
        menu.setStatus(0);
        assertTrue(sysMenuService.updateById(menu));

        assertFalse(permissionCache.containsKey("admin"), "管理员权限列表包含所有启用菜单");
        assertFalse(permissionCache.containsKey("alice"));
        assertFalse(permissionCache.containsKey("bob"));
        assertTrue(permissionCache.containsKey("carol"));
    }

    @Test
    void creatingAndDeletingMenuRemovesAdministratorsCachedPermissionJson() {
        permissionCache.put("admin", "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put("alice", "[{\"authority\":\"bnt.approval.view\"}]");

        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setName("新权限");
        menu.setType(2);
        menu.setPath("new-permission");
        menu.setPerms("bnt.new.permission");
        menu.setStatus(1);
        assertTrue(sysMenuService.save(menu));

        assertFalse(permissionCache.containsKey("admin"));
        assertTrue(permissionCache.containsKey("alice"));

        permissionCache.put("admin", "[{\"authority\":\"bnt.new.permission\"}]");
        sysMenuService.removeMenuById(menu.getId());
        assertFalse(permissionCache.containsKey("admin"));
        assertTrue(permissionCache.containsKey("alice"));
    }

    @Test
    void rollingBackRoleChangesKeepsTheExistingPermissionJson() {
        permissionCache.put("alice", "[{\"authority\":\"bnt.approval.view\"}]");

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            AssginRoleVo assignment = new AssginRoleVo();
            assignment.setUserId(2L);
            assignment.setRoleIdList(List.of(11L));
            sysRoleService.doAssign(assignment);
            throw new IllegalStateException("force database rollback");
        }));

        assertTrue(permissionCache.containsKey("alice"));
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, 2L);
        assertEquals(10L, sysUserRoleService.list(wrapper).get(0).getRoleId());
    }

    @TestConfiguration
    static class InMemoryRedisConfig {
        @Bean
        ConcurrentMap<String, String> permissionCacheStore() {
            return new ConcurrentHashMap<>();
        }

        @Bean
        @Primary
        RedisTemplate<String, String> redisTemplate(ConcurrentMap<String, String> permissionCacheStore) {
            RedisTemplate<String, String> template = new RedisTemplate<>() {
                @Override
                @SuppressWarnings("unchecked")
                public ValueOperations<String, String> opsForValue() {
                    return (ValueOperations<String, String>) Proxy.newProxyInstance(
                            ValueOperations.class.getClassLoader(),
                            new Class<?>[]{ValueOperations.class},
                            (proxy, method, args) -> {
                                if ("set".equals(method.getName()) && args != null && args.length >= 2) {
                                    permissionCacheStore.put(String.valueOf(args[0]), String.valueOf(args[1]));
                                    return null;
                                }
                                if ("get".equals(method.getName()) && args != null && args.length >= 1) {
                                    return permissionCacheStore.get(String.valueOf(args[0]));
                                }
                                if ("toString".equals(method.getName())) {
                                    return "PermissionCacheTestRedis";
                                }
                                throw new UnsupportedOperationException(method.getName());
                            });
                }

                @Override
                public Boolean delete(String key) {
                    return permissionCacheStore.remove(key) != null;
                }

                @Override
                public void afterPropertiesSet() {
                }
            };
            return template;
        }
    }
}
