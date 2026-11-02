package com.lautung.oa.auth.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * auth 域的 Mapper 注册：扫描范围随域模块走。
 */
@Configuration
@MapperScan(basePackages = "com.lautung.oa.auth.mapper", markerInterface = BaseMapper.class)
public class AuthMapperConfiguration {
}
