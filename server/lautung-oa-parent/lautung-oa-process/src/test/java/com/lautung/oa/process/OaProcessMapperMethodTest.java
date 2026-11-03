package com.lautung.oa.process;

import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.vo.process.ProcessQueryVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OaProcessMapperMethodTest {

    @Test
    public void customPaginationMustNotReuseBaseMapperSelectPageName() throws Exception {
        Method method = OaProcessMapper.class.getMethod(
                "selectProcessPage",
                Page.class,
                ProcessQueryVo.class,
                DataScope.class
        );

        assertEquals("selectProcessPage", method.getName());

        Param voParam = method.getParameters()[1].getAnnotation(Param.class);
        assertNotNull(voParam);
        assertEquals("vo", voParam.value());

        // 数据范围是参数不是 SQL 片段（ADR 0053/0057）：第三个入参绑定给 XML 的 scope。
        Param scopeParam = method.getParameters()[2].getAnnotation(Param.class);
        assertNotNull(scopeParam);
        assertEquals("scope", scopeParam.value());
    }
}
