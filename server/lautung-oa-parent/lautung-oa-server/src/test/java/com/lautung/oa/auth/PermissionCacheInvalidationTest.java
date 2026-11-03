package com.lautung.oa.auth;

import com.lautung.oa.auth.service.SysRoleService;
import com.lautung.oa.security.cache.PermissionCacheKey;
import com.lautung.oa.auth.service.SysMenuService;
import com.lautung.oa.auth.service.SysUserRoleService;
import com.lautung.oa.model.system.SysMenu;
import com.lautung.oa.model.system.SysRole;
import com.lautung.oa.model.system.SysUserRole;
import com.lautung.oa.vo.system.AssginMenuVo;
import com.lautung.oa.vo.system.AssginRoleVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_permission_cache;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/authority-cache-contract-schema.sql"})
class PermissionCacheInvalidationTest {

    private static final Long CACHE_ALICE_ID = 102L;
    private static final String CACHE_ALICE = "cache-alice";
    private static final String CACHE_BOB = "cache-bob";
    private static final String CACHE_CAROL = "cache-carol";

    /** #237 迁移后的键形状：失效断言都落在唯一构造点的 perm:cache: 前缀上。 */
    private static String key(String username) {
        return PermissionCacheKey.of(username);
    }

    @Autowired
    private SysRoleService sysRoleService;

    @Autowired
    private SysMenuService sysMenuService;

    @Autowired
    private SysUserRoleService sysUserRoleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ConcurrentMap<String, String> permissionCache;

    @Test
    void changingUsersRolesImmediatelyRemovesOnlyThatUsersCachedPermissionJson() {
        permissionCache.put(key(CACHE_ALICE), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_BOB), "[{\"authority\":\"bnt.approval.view\"}]");

        AssginRoleVo assignment = new AssginRoleVo();
        assignment.setUserId(CACHE_ALICE_ID);
        assignment.setRoleIdList(List.of(11L));
        sysRoleService.doAssign(assignment);

        assertFalse(permissionCache.containsKey(key(CACHE_ALICE)), "被改角色的用户不能继续使用旧权限 JSON");
        assertTrue(permissionCache.containsKey(key(CACHE_BOB)), "未改角色的用户缓存应保留");
    }

    @Test
    void changingMenusForARoleImmediatelyRemovesPermissionJsonForEveryMember() {
        permissionCache.put(key(CACHE_ALICE), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_BOB), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_CAROL), "[{\"authority\":\"bnt.manage.view\"}]");

        AssginMenuVo assignment = new AssginMenuVo();
        assignment.setRoleId(10L);
        assignment.setMenuIdList(List.of(21L));
        sysMenuService.doAssign(assignment);

        assertFalse(permissionCache.containsKey(key(CACHE_ALICE)), "角色菜单变化后应清除第一位成员的旧权限");
        assertFalse(permissionCache.containsKey(key(CACHE_BOB)), "角色菜单变化后应清除第二位成员的旧权限");
        assertTrue(permissionCache.containsKey(key(CACHE_CAROL)), "无关角色成员缓存应保留");
    }

    @Test
    void changingRoleDetailsRemovesCachedPermissionJsonForEveryMember() {
        permissionCache.put(key(CACHE_ALICE), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_BOB), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_CAROL), "[{\"authority\":\"bnt.manage.view\"}]");

        SysRole role = sysRoleService.getById(10L);
        role.setRoleName("已更新审批人");
        assertTrue(sysRoleService.updateById(role));

        assertFalse(permissionCache.containsKey(key(CACHE_ALICE)));
        assertFalse(permissionCache.containsKey(key(CACHE_BOB)));
        assertTrue(permissionCache.containsKey(key(CACHE_CAROL)));
    }

    @Test
    void deletingRoleRemovesCachedPermissionJsonForItsMembers() {
        permissionCache.put(key(CACHE_ALICE), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_BOB), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_CAROL), "[{\"authority\":\"bnt.manage.view\"}]");

        assertTrue(sysRoleService.removeById(10L));

        assertFalse(permissionCache.containsKey(key(CACHE_ALICE)));
        assertFalse(permissionCache.containsKey(key(CACHE_BOB)));
        assertTrue(permissionCache.containsKey(key(CACHE_CAROL)));
    }

    @Test
    void changingMenuDetailsRemovesCachedPermissionJsonForRoleMembers() {
        permissionCache.put(key(CACHE_ALICE), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_BOB), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_CAROL), "[{\"authority\":\"bnt.manage.view\"}]");

        SysMenu menu = sysMenuService.getById(20L);
        menu.setStatus(0);
        assertTrue(sysMenuService.updateById(menu));

        assertFalse(permissionCache.containsKey(key(CACHE_ALICE)));
        assertFalse(permissionCache.containsKey(key(CACHE_BOB)));
        assertTrue(permissionCache.containsKey(key(CACHE_CAROL)));
    }

    @Test
    void adminIsInvalidatedAsARoleMemberNotAsASeedAccount() {
        permissionCache.put(key("admin"), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_ALICE), "[{\"authority\":\"bnt.approval.view\"}]");
        jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id, is_deleted) VALUES (40, 1, 10, 0)");

        SysMenu menu = sysMenuService.getById(20L);
        menu.setStatus(0);
        assertTrue(sysMenuService.updateById(menu));

        assertFalse(permissionCache.containsKey(key("admin")), "admin 是角色成员，按成员失效");
        assertFalse(permissionCache.containsKey(key(CACHE_ALICE)));
    }

    @Test
    void creatingAndDeletingUnassignedMenuKeepsCachedPermissionJson() {
        permissionCache.put(key("admin"), "[{\"authority\":\"bnt.approval.view\"}]");
        permissionCache.put(key(CACHE_ALICE), "[{\"authority\":\"bnt.approval.view\"}]");

        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setName("新权限");
        menu.setType(2);
        menu.setPath("new-permission");
        menu.setPerms("bnt.new.permission");
        menu.setStatus(1);
        assertTrue(sysMenuService.save(menu));

        assertTrue(permissionCache.containsKey(key("admin")));
        assertTrue(permissionCache.containsKey(key(CACHE_ALICE)));

        sysMenuService.removeMenuById(menu.getId());
        assertTrue(permissionCache.containsKey(key("admin")));
        assertTrue(permissionCache.containsKey(key(CACHE_ALICE)));
    }

    @Test
    void rollingBackRoleChangesKeepsTheExistingPermissionJson() {
        permissionCache.put(key(CACHE_ALICE), "[{\"authority\":\"bnt.approval.view\"}]");

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            AssginRoleVo assignment = new AssginRoleVo();
            assignment.setUserId(CACHE_ALICE_ID);
            assignment.setRoleIdList(List.of(11L));
            sysRoleService.doAssign(assignment);
            throw new IllegalStateException("force database rollback");
        }));

        assertTrue(permissionCache.containsKey(key(CACHE_ALICE)));
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, CACHE_ALICE_ID);
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
