package com.lautung.oa.process;

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
                ProcessQueryVo.class
        );

        assertEquals("selectProcessPage", method.getName());

        Param param = method.getParameters()[1].getAnnotation(Param.class);
        assertNotNull(param);
        assertEquals("vo", param.value());
    }
}
