package com.atguigu.auth.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * auth 域的 Mapper 注册：扫描范围随域模块走，包名字符串在 E2 包名替换前保持
 * {@code com.atguigu} 原样。
 */
@Configuration
@MapperScan(basePackages = "com.atguigu.auth.mapper", markerInterface = BaseMapper.class)
public class AuthMapperConfiguration {
}
