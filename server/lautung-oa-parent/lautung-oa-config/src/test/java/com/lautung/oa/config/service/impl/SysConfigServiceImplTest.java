package com.lautung.oa.config.service.impl;

import com.lautung.oa.config.mapper.ConfigMapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 参数软删（PR #260 审查）：让出唯一键的更新失败时不得继续删行，且整个动作在事务内。
 */
class SysConfigServiceImplTest {

    private final ConfigMapper configMapper = mock(ConfigMapper.class);

    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

    private final SysConfigServiceImpl service = new SysConfigServiceImpl(redisTemplate);

    @BeforeEach
    void injectCollaborators() {
        ReflectionTestUtils.setField(service, "baseMapper", configMapper);
    }

    @Test
    void removeConfigIsTransactionalAndStopsWhenReleaseUpdateAffectsNoRow() throws Exception {
        when(configMapper.selectById(5L)).thenReturn(config(5L, "t252_key"));
        when(configMapper.updateById(any(SysConfig.class))).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.removeConfig(5L));

        assertEquals(201, error.getCode());
        assertEquals("参数不存在", error.getMsg());
        verify(configMapper, never()).deleteById(any(java.io.Serializable.class));
        assertNotNull(SysConfigServiceImpl.class.getMethod("removeConfig", Long.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void removeConfigDeletesWhenReleaseUpdateSucceeds() {
        when(configMapper.selectById(5L)).thenReturn(config(5L, "t252_key"));
        when(configMapper.updateById(any(SysConfig.class))).thenReturn(1);
        when(configMapper.deleteById(5L)).thenReturn(1);

        service.removeConfig(5L);

        verify(configMapper).deleteById(5L);
        verify(redisTemplate).delete("config:t252_key");
    }

    private static SysConfig config(Long id, String key) {
        SysConfig config = new SysConfig();
        config.setId(id);
        config.setConfigKey(key);
        config.setConfigValue("value");
        config.setConfigName("t252 参数");
        config.setValueType(SysConfig.TYPE_STRING);
        config.setBuiltin(SysConfig.BUILTIN_NO);
        return config;
    }
}
