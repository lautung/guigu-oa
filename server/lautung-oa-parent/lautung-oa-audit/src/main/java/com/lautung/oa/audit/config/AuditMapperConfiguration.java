package com.lautung.oa.audit.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * audit 域的 Mapper 注册：扫描范围随模块走（与 auth/process 同一约定）。
 *
 * <p>不限定 {@code markerInterface}：{@link com.lautung.oa.audit.mapper.AuditOperatorMapper}
 * 是一条只读快照查询，没有对应实体，也就不继承 {@code BaseMapper}。
 */
@Configuration
@MapperScan(basePackages = "com.lautung.oa.audit.mapper")
public class AuditMapperConfiguration {
}
