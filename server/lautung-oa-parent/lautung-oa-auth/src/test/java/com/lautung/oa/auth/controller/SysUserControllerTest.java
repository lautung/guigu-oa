package com.lautung.oa.auth.controller;

import com.lautung.oa.auth.datascope.DataScopeResolver;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.system.SysUserQueryVo;
import com.lautung.oa.vo.system.SysUserResetPasswordRequest;
import com.lautung.oa.vo.system.SysUserSaveRequest;
import com.lautung.oa.vo.system.SysUserUpdateRequest;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserControllerTest {

    private final SysUserService service = mock(SysUserService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final DataScopeResolver dataScopeResolver = mock(DataScopeResolver.class);
    private final com.lautung.oa.auth.service.SysDeptService sysDeptService =
            mock(com.lautung.oa.auth.service.SysDeptService.class);
    private final com.lautung.oa.auth.service.SysPostService sysPostService =
            mock(com.lautung.oa.auth.service.SysPostService.class);
    private final SysUserController controller = new SysUserController();

    @BeforeAll
    static void initTableMetadata() {
        // Lambda wrapper 需要 SysUser 的列元数据，纯单元测试里手工初始化（testing.md）。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
    }

    @BeforeEach
    void injectDependencies() {
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(controller, "dataScopeResolver", dataScopeResolver);
        ReflectionTestUtils.setField(controller, "sysDeptService", sysDeptService);
        ReflectionTestUtils.setField(controller, "sysPostService", sysPostService);
        when(dataScopeResolver.resolve(any())).thenReturn(DataScope.unrestricted());
    }

    @AfterEach
    void clearLoginUser() {
        LoginUserInfoHelper.removeUserId();
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
    void scopesTheUserPageByDepartmentAndOwnRow() {
        LoginUserInfoHelper.setUserId(7L);
        when(dataScopeResolver.resolve(7L)).thenReturn(DataScope.of(Set.of(10L, 11L), 7L));

        controller.index(1L, 10L, new SysUserQueryVo());

        String sql = capturedWrapper().getSqlSegment();
        assertTrue(sql.contains("dept_id IN"), sql);
        assertTrue(sql.contains("OR"), sql);
        assertTrue(sql.contains("id ="), sql);
    }

    @Test
    void emptyDataScopeReturnsZeroRowsInsteadOfEverything() {
        LoginUserInfoHelper.setUserId(7L);
        when(dataScopeResolver.resolve(7L)).thenReturn(DataScope.of(Set.of(), null));

        controller.index(1L, 10L, new SysUserQueryVo());

        String sql = capturedWrapper().getSqlSegment();
        assertTrue(sql.contains("1 = 0"), sql);
        assertFalse(sql.contains("dept_id"), sql);
    }

    @Test
    void selfOnlyScopeUsesTheUserRowWithoutADepartmentPredicate() {
        LoginUserInfoHelper.setUserId(7L);
        when(dataScopeResolver.resolve(7L)).thenReturn(DataScope.of(Set.of(), 7L));

        controller.index(1L, 10L, new SysUserQueryVo());

        String sql = capturedWrapper().getSqlSegment();
        assertTrue(sql.contains("id ="), sql);
        assertFalse(sql.contains("dept_id"), sql);
        assertFalse(sql.contains("1 = 0"), sql);
    }

    @Test
    void unrestrictedScopeAddsNoPredicate() {
        LoginUserInfoHelper.setUserId(7L);
        when(dataScopeResolver.resolve(7L)).thenReturn(DataScope.unrestricted());

        controller.index(1L, 10L, new SysUserQueryVo());

        String sql = capturedWrapper().getSqlSegment();
        assertEquals("", sql);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<SysUser> capturedWrapper() {
        ArgumentCaptor<Wrapper<SysUser>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(any(), wrapper.capture());
        return (LambdaQueryWrapper<SysUser>) wrapper.getValue();
    }

    @Test
    void readsASingleUserWithTheirPosts() {
        com.lautung.oa.vo.system.SysUserDetailVo detail = new com.lautung.oa.vo.system.SysUserDetailVo();
        detail.setId(3L);
        detail.setPostIds(List.of(7L, 8L));
        when(service.findDetail(3L)).thenReturn(detail);

        assertEquals(detail, controller.get(3L).getData());
    }

    @Test
    void alwaysEncodesTheSubmittedPasswordOnSave() {
        when(passwordEncoder.encode("111111")).thenReturn("hashed");

        SysUserSaveRequest request = new SysUserSaveRequest();
        request.setUsername("new-user");
        request.setPassword("111111");
        request.setName("新用户");
        controller.save(request);

        ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
        verify(service).saveUser(saved.capture(), isNull());
        assertEquals("hashed", saved.getValue().getPassword());
        assertEquals("new-user", saved.getValue().getUsername());
        assertEquals("新用户", saved.getValue().getName());
        // 创建不给主键：save 只创建（ADR 0034）。
        assertNull(saved.getValue().getId());
        // 空白密码在建账号处就被 201 挡下（RequestValidationContractTest），这里不再有跳过编码的分支。
        verify(passwordEncoder, never()).encode("");
    }

    @Test
    void leavesThePasswordColumnAloneWhenAnUpdateOmitsIt() {
        SysUser existing = new SysUser();
        existing.setId(3L);
        existing.setPassword("stored");
        when(service.getById(3L)).thenReturn(existing);

        SysUserUpdateRequest update = new SysUserUpdateRequest();
        update.setId(3L);
        controller.updateById(update);

        ArgumentCaptor<SysUser> written = ArgumentCaptor.forClass(SysUser.class);
        verify(service).updateUser(written.capture(), isNull());
        assertNull(written.getValue().getPassword());
        // 公共字段由 CommonFieldsMetaObjectHandler 填（ADR 0031），控制器不再自己写 update_time。
        assertNull(written.getValue().getUpdateTime());
        assertNull(written.getValue().getTokenVersion());
        verify(passwordEncoder, never()).encode(any());
        verify(service, never()).changePassword(any(), any());
    }

    @Test
    void doesNotWriteAPasswordForAMissingRow() {
        SysUserUpdateRequest update = new SysUserUpdateRequest();
        update.setId(3L);
        update.setPassword("new-secret");
        when(service.getById(3L)).thenReturn(null);

        controller.updateById(update);

        verify(passwordEncoder, never()).encode(any());
        verify(service, never()).changePassword(any(), any());
        verify(service, never()).updateById(any());
        verify(service, never()).updateUser(any(), any());
    }

    @Test
    void passesPostIdsToTheTransactionalUserWrite() {
        SysUser existing = new SysUser();
        existing.setId(3L);
        existing.setPassword("stored");
        when(service.getById(3L)).thenReturn(existing);
        List<Long> postIds = List.of(7L, 8L);

        SysUserUpdateRequest update = new SysUserUpdateRequest();
        update.setId(3L);
        update.setPostIds(postIds);
        controller.updateById(update);
        verify(service).updateUser(any(SysUser.class), eq(postIds));

        SysUserSaveRequest save = new SysUserSaveRequest();
        save.setUsername("new-user");
        save.setPassword("111111");
        save.setName("新用户");
        save.setPostIds(postIds);
        when(passwordEncoder.encode("111111")).thenReturn("hashed");
        controller.save(save);
        verify(service).saveUser(any(SysUser.class), eq(postIds));
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
        SysUserUpdateRequest update = new SysUserUpdateRequest();
        update.setId(3L);
        update.setPassword("new-secret");

        controller.updateById(update);

        ArgumentCaptor<SysUser> written = ArgumentCaptor.forClass(SysUser.class);
        InOrder order = inOrder(service);
        order.verify(service).changePassword(3L, "hashed-new");
        order.verify(service).updateUser(written.capture(), isNull());
        assertNull(written.getValue().getPassword());
    }

    @Test
    void leavesAnIdenticalPasswordUnhashedOnUpdate() {
        SysUser existing = new SysUser();
        existing.setId(3L);
        existing.setPassword("stored-hash");
        when(service.getById(3L)).thenReturn(existing);
        when(passwordEncoder.matches("same", "stored-hash")).thenReturn(true);
        SysUserUpdateRequest update = new SysUserUpdateRequest();
        update.setId(3L);
        update.setPassword("same");

        controller.updateById(update);

        ArgumentCaptor<SysUser> written = ArgumentCaptor.forClass(SysUser.class);
        verify(service).updateUser(written.capture(), isNull());
        assertNull(written.getValue().getPassword());
        assertNull(written.getValue().getTokenVersion());
        verify(passwordEncoder, never()).encode(any());
        verify(service, never()).changePassword(any(), any());
    }

    @Test
    void resetsThePasswordWithoutRequiringTheOldOne() {
        SysUser existing = new SysUser();
        existing.setId(3L);
        existing.setPassword("stored-hash");
        when(service.getById(3L)).thenReturn(existing);
        when(passwordEncoder.encode("fresh-secret")).thenReturn("hashed-fresh");
        SysUserResetPasswordRequest reset = new SysUserResetPasswordRequest();
        reset.setNewPassword("fresh-secret");

        assertEquals(200, controller.resetPassword(3L, reset).getCode());

        verify(passwordEncoder, never()).matches(any(), any());
        verify(service).changePassword(3L, "hashed-fresh");
        verify(service, never()).updateById(any());
    }

    @Test
    void doesNotWriteAPasswordWhenTheResetTargetIsMissing() {
        when(service.getById(3L)).thenReturn(null);
        SysUserResetPasswordRequest reset = new SysUserResetPasswordRequest();
        reset.setNewPassword("fresh-secret");

        assertEquals(200, controller.resetPassword(3L, reset).getCode());

        verify(passwordEncoder, never()).encode(any());
        verify(service, never()).changePassword(any(), any());
    }
}
