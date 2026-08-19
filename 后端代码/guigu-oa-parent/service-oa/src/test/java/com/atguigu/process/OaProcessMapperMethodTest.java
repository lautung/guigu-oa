package com.atguigu.process;

import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.vo.process.ProcessQueryVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class OaProcessMapperMethodTest {

    @Test
    public void customPaginationMustNotReuseBaseMapperSelectPageName() throws Exception {
        Method method = OaProcessMapper.class.getMethod(
                "selectProcessPage",
                Page.class,
                ProcessQueryVo.class
        );

        assertEquals("selectProcessPage", method.getName());

        Param param = method.getParameters()[1].getAnnotation(Param.class);
        assertNotNull(param);
        assertEquals("vo", param.value());
    }
}
