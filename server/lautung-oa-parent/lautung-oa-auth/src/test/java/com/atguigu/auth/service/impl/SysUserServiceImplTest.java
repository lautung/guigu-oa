package com.atguigu.auth.service.impl;

import com.atguigu.auth.mapper.SysUserMapper;
import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.model.system.SysUser;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户名唯一的 service 层语义（ADR 0042）：查重先行给出命名拒绝，并发漏网的
 * {@code DuplicateKeyException} 翻译成同一文案；删除让位随软删同事务发生。
 */
class SysUserServiceImplTest {

    private final SysUserMapper mapper = mock(SysUserMapper.class);
    private final SysUserServiceImpl service = new SysUserServiceImpl();

    @BeforeAll
    static void initTableMetadata() {
        // Lambda wrapper 需要 SysUser 的列元数据，纯单元测试里手工初始化（testing.md）。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
    }

    @BeforeEach
    void injectMapper() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void rejectsADuplicateUsernameBeforeWriting() {
        when(mapper.selectCount(any())).thenReturn(1L);
        SysUser user = new SysUser();
        user.setUsername("zhangsan");

        GuiguException rejected = assertThrows(GuiguException.class, () -> service.save(user));

        assertEquals(201, rejected.getCode());
        assertEquals("用户名已存在", rejected.getMessage());
        verify(mapper, never()).insert(any(SysUser.class));
    }

    @Test
    void translatesADuplicateKeyOnInsertIntoTheNamedReject() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("uk_sys_user_username"));
        SysUser user = new SysUser();
        user.setUsername("zhangsan");

        GuiguException rejected = assertThrows(GuiguException.class, () -> service.save(user));

        assertEquals(201, rejected.getCode());
        assertEquals("用户名已存在", rejected.getMessage());
    }

    @Test
    void translatesADuplicateKeyOnUpdateIntoTheNamedReject() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.updateById(any(SysUser.class))).thenThrow(new DuplicateKeyException("uk_sys_user_username"));
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("zhangsan");

        GuiguException rejected = assertThrows(GuiguException.class, () -> service.updateById(user));

        assertEquals(201, rejected.getCode());
        assertEquals("用户名已存在", rejected.getMessage());
    }

    @Test
    void skipsTheUniquenessCheckWhenTheUpdateDoesNotRename() {
        when(mapper.updateById(any(SysUser.class))).thenReturn(1);
        SysUser user = new SysUser();
        user.setId(7L);

        service.updateById(user);

        verify(mapper, never()).selectCount(any());
    }

    @Test
    void freesTheUsernameBeforeTheSoftDelete() {
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(mapper.deleteById(anyLong())).thenReturn(1);

        assertTrue(service.removeById(7L));

        // 先让位（置空用户名的 UPDATE）再软删，两步同事务由 @Transactional 保证。
        verify(mapper).update(isNull(), any());
        verify(mapper).deleteById(7L);
    }
}
