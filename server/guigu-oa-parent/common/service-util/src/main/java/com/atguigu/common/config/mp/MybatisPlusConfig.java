package com.atguigu.common.config.mp;

import com.atguigu.common.page.PageWindow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {
        "com.atguigu.auth.mapper",
        "com.atguigu.process.mapper",
        "com.atguigu.process.attachment"
}, markerInterface = BaseMapper.class)
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        // 兜住绕过 HTTP 入口的 MyBatis-Plus 分页查询；Flowable 任务查询与原生 LIMIT 由 PageWindow 负责。
        pagination.setMaxLimit(PageWindow.MAX_LIMIT);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
