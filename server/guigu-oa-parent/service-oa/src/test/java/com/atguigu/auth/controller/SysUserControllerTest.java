package com.atguigu.auth.controller;

import com.atguigu.auth.service.SysUserService;
import com.atguigu.common.result.Result;
import com.atguigu.model.system.SysUser;
import com.atguigu.vo.system.SysUserQueryVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserControllerTest {

    private final SysUserService service = mock(SysUserService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SysUserController controller = new SysUserController();

    @BeforeEach
    void injectDependencies() {
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "passwordEncoder", passwordEncoder);
    }

    @Test
    void updatesTheStatusAndDeletesUsers() {
        assertEquals(200, controller.updateStatus(3L, 1).getCode());
        assertEquals(200, controller.remove(3L).getCode());

        verify(service).updateStatus(3L, 1);
        verify(service).removeById(3L);
    }

    @Test
    void pagesUsersWithEveryOptionalFilter() {
        when(service.page(any(), any())).thenReturn(new Page<>(1, 10));

        Result blank = controller.index(1L, 10L, new SysUserQueryVo());
        assertEquals(200, blank.getCode());

        SysUserQueryVo filtered = new SysUserQueryVo();
        filtered.setKeyword("张三");
        filtered.setCreateTimeBegin("2026-01-01");
        filtered.setCreateTimeEnd("2026-01-31");
        Result result = controller.index(1L, 10L, filtered);

        assertEquals(200, result.getCode());
        assertTrue(result.getData() instanceof Page);
    }

    @Test
    void readsASingleUser() {
        SysUser user = new SysUser();
        user.setId(3L);
        when(service.getById(3L)).thenReturn(user);

        assertEquals(user, controller.get(3L).getData());
    }

    @Test
    void hashesANewPasswordOnSaveButLeavesBlankPasswordsAlone() {
        when(passwordEncoder.encode("111111")).thenReturn("hashed");

        SysUser withPassword = new SysUser();
        withPassword.setPassword("111111");
        controller.save(withPassword);
        assertEquals("hashed", withPassword.getPassword());

        SysUser withoutPassword = new SysUser();
        controller.save(withoutPassword);
        verify(passwordEncoder, never()).encode("");

        verify(service, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void leavesThePasswordColumnAloneWhenAnUpdateOmitsIt() {
        SysUser existing = new SysUser();
        existing.setId(3L);
        existing.setPassword("stored");
        when(service.getById(3L)).thenReturn(existing);

        SysUser update = new SysUser();
        update.setId(3L);
        controller.updateById(update);

        assertNull(update.getPassword());
        assertNotNull(update.getUpdateTime());
        assertNull(update.getTokenVersion());
        verify(passwordEncoder, never()).encode(any());
        verify(service, never()).changePassword(any(), any());
        verify(service).updateById(update);
    }

    @Test
    void doesNotWriteAPasswordForAMissingRow() {
        SysUser update = new SysUser();
        update.setId(3L);
        update.setPassword("new-secret");
        when(service.getById(3L)).thenReturn(null);

        controller.updateById(update);

        verify(passwordEncoder, never()).encode(any());
        verify(service, never()).changePassword(any(), any());
        verify(service, never()).updateById(any());
    }

    @Test
    void changesThePasswordAndItsVersionTogether() {
        SysUser existing = new SysUser();
        existing.setId(3L);
        existing.setPassword("stored");
        existing.setTokenVersion(2L);
        when(service.getById(3L)).thenReturn(existing);
        when(passwordEncoder.matches("new-secret", "stored")).thenReturn(false);
        when(passwordEncoder.encode("new-secret")).thenReturn("hashed-new");
        SysUser update = new SysUser();
        update.setId(3L);
        update.setPassword("new-secret");

        controller.updateById(update);

        assertNull(update.getPassword());
        InOrder order = inOrder(service);
        order.verify(service).changePassword(3L, "hashed-new");
        order.verify(service).updateById(update);
    }

    @Test
    void leavesAnIdenticalPasswordUnhashedOnUpdate() {
        SysUser existing = new SysUser();
        existing.setId(3L);
        existing.setPassword("stored-hash");
        when(service.getById(3L)).thenReturn(existing);
        when(passwordEncoder.matches("same", "stored-hash")).thenReturn(true);
        SysUser update = new SysUser();
        update.setId(3L);
        update.setPassword("same");

        controller.updateById(update);

        assertNull(update.getPassword());
        assertNull(update.getTokenVersion());
        verify(passwordEncoder, never()).encode(any());
        verify(service, never()).changePassword(any(), any());
    }
}
