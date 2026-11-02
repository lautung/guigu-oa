package com.lautung.oa.audit.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.audit.entity.SysOperLog;
import com.lautung.oa.audit.mapper.SysOperLogMapper;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.system.SysOperLogQueryVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 列表排序（ADR 0045/0054）：{@code create_time DESC} 之后必须再按 {@code id DESC} 定序。
 *
 * <p>合同测试只看 H2 返回的顺序，同 {@code create_time} 的两行即使少了 id 次序也可能碰巧绿；
 * 这里直接钉住 wrapper 生成的 ORDER BY 片段，让「只按时间排」变红。
 */
class SysOperLogServiceTest {

    private final SysOperLogMapper operLogMapper = mock(SysOperLogMapper.class);

    private final SysOperLogService service = new SysOperLogService(operLogMapper);

    @BeforeAll
    static void initTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysOperLog.class);
    }

    @Test
    void pageOrdersByCreateTimeThenId() {
        Page<SysOperLog> page = new Page<>(1, 10);
        when(operLogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        service.page(page, new SysOperLogQueryVo(), DataScope.unrestricted());

        ArgumentCaptor<Wrapper<SysOperLog>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(operLogMapper).selectPage(any(Page.class), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();

        int createTime = sql.indexOf("create_time DESC");
        int id = sql.indexOf("id DESC");
        assertTrue(createTime >= 0, "missing create_time DESC in " + sql);
        assertTrue(id > createTime, "missing id DESC tie-breaker after create_time in " + sql);
    }
}
