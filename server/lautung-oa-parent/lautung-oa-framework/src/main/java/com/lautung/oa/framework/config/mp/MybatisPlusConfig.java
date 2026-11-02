package com.lautung.oa.framework.config.mp;

import com.lautung.oa.framework.page.PageWindow;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 的通用装配：分页拦截器与公共字段处理器。
 *
 * <p>Mapper 注册下沉到各域模块（{@code com.lautung.oa.auth.config.AuthMapperConfiguration}、
 * {@code com.lautung.oa.process.config.ProcessMapperConfiguration}）：framework 不再知道任何域包。
 */
@Configuration
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

    @Bean
    public MetaObjectHandler commonFieldsMetaObjectHandler() {
        return new CommonFieldsMetaObjectHandler();
    }
}
