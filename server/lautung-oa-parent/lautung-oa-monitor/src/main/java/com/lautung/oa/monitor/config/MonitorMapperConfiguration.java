package com.lautung.oa.monitor.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * monitor 域的 Mapper 注册：扫描范围随模块走（与 auth/audit/notice 同一约定）。
 */
@Configuration
@MapperScan(basePackages = "com.lautung.oa.monitor.mapper")
public class MonitorMapperConfiguration {
}
