package com.lautung.oa.audit.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.audit.entity.SysLoginLog;
import com.lautung.oa.audit.mapper.SysLoginLogMapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.security.login.LoginAttemptLimiter;
import com.lautung.oa.vo.system.SysLoginLogQueryVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录日志查询面的 wrapper 纪律（ADR 0045/0057，#216）：排序带 id 定序、数据范围落在
 * {@code dept_id}/{@code user_id}、空集是零行、空账号不触发解锁。
 *
 * <p>合同测试看 H2 返回结果；这里直接钉 wrapper 的 SQL 片段，让「只按时间排」「范围落到
 * 错的列」「空集悄悄变成全部」这类改动变红。
 */
class SysLoginLogServiceTest {

    private final SysLoginLogMapper loginLogMapper = mock(SysLoginLogMapper.class);

    private final LoginAttemptLimiter loginAttemptLimiter = mock(LoginAttemptLimiter.class);

    private final SysLoginLogService service = new SysLoginLogService(loginLogMapper, loginAttemptLimiter);

    @BeforeAll
    static void initTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysLoginLog.class);
    }

    @Test
    void pageOrdersByCreateTimeThenId() {
        Page<SysLoginLog> page = new Page<>(1, 10);
        when(loginLogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        service.page(page, new SysLoginLogQueryVo(), DataScope.unrestricted());

        String sql = capturedSql();
        int createTime = sql.indexOf("create_time DESC");
        int id = sql.indexOf("id DESC");
        assertTrue(createTime >= 0, "missing create_time DESC in " + sql);
        assertTrue(id > createTime, "missing id DESC tie-breaker after create_time in " + sql);
    }

    @Test
    void visibleDeptScopeUsesTheDeptAndUserColumns() {
        Page<SysLoginLog> page = new Page<>(1, 10);
        when(loginLogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        service.page(page, new SysLoginLogQueryVo(), DataScope.of(Set.of(81L, 82L), 184L));

        String sql = capturedSql();
        assertTrue(sql.contains("dept_id IN"), "department column must be dept_id in " + sql);
        assertTrue(sql.contains("user_id ="), "self column must be user_id in " + sql);
    }

    @Test
    void anEmptyScopeIsZeroRowsNotEverything() {
        Page<SysLoginLog> page = new Page<>(1, 10);
        when(loginLogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        service.page(page, new SysLoginLogQueryVo(), DataScope.of(Set.of(), null));

        assertTrue(capturedSql().contains("1 = 0"), "empty scope must become 1 = 0");
    }

    @Test
    void unlockRejectsABlankUsernameWithTheNamedMessage() {
        for (String blank : new String[]{null, "", "   "}) {
            BusinessException error = assertThrows(BusinessException.class, () -> service.unlock(blank));
            assertEquals(201, error.getCode());
            assertEquals(SysLoginLogService.UNLOCK_USERNAME_REQUIRED, error.getMsg());
        }
        verify(loginAttemptLimiter, never()).unlockAccount(any());
    }

    private String capturedSql() {
        ArgumentCaptor<Wrapper<SysLoginLog>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(loginLogMapper).selectPage(any(Page.class), wrapper.capture());
        return wrapper.getValue().getSqlSegment();
    }
}
