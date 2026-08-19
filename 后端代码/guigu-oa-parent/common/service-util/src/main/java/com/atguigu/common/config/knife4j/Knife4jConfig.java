package com.atguigu.common.config.knife4j;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("后台管理系统-API文档")
                        .description("本文档描述了后台管理系统微服务接口定义")
                        .version("1.0")
                        .contact(new Contact()
                                .name("atguigu")
                                .url("http://atguigu.com")
                                .email("atguigu@qq.com")));
    }
}
