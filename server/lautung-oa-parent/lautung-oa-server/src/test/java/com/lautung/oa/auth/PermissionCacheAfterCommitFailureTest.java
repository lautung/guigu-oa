package com.lautung.oa.auth;

import com.lautung.oa.auth.service.SysRoleService;
import com.lautung.oa.auth.service.SysUserRoleService;
import com.lautung.oa.model.system.SysUserRole;
import com.lautung.oa.vo.system.AssginRoleVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_permission_cache_after_commit;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/authority-cache-contract-schema.sql"})
class PermissionCacheAfterCommitFailureTest {

    private static final Long CACHE_ALICE_ID = 102L;

    @Autowired
    private SysRoleService sysRoleService;

    @Autowired
    private SysUserRoleService sysUserRoleService;

    @Test
    void assignmentStaysCommittedWhenPermissionCacheInvalidationFailsAfterCommit() {
        AssginRoleVo assignment = new AssginRoleVo();
        assignment.setUserId(CACHE_ALICE_ID);
        assignment.setRoleIdList(List.of(11L));

        assertThrows(IllegalStateException.class, () -> sysRoleService.doAssign(assignment));

        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, CACHE_ALICE_ID);
        assertEquals(
                List.of(11L),
                sysUserRoleService.list(wrapper).stream().map(SysUserRole::getRoleId).toList());
    }

    @TestConfiguration
    static class FailingRedisConfig {
        @Bean
        @Primary
        RedisTemplate<String, String> redisTemplate() {
            return new RedisTemplate<>() {
                @Override
                public Boolean delete(String key) {
                    throw new IllegalStateException("Redis 失效失败");
                }

                @Override
                public void afterPropertiesSet() {
                }
            };
        }
    }
}
