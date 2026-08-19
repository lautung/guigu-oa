package com.atguigu.common.config.mp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {"com.atguigu.auth.mapper", "com.atguigu.process.mapper"})
public class MybatisPlusConfig {
}
