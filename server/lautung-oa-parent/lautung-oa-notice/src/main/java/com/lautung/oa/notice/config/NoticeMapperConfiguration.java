package com.lautung.oa.notice.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * notice 域的 Mapper 注册：只扫本域（与 auth/process/audit/history 同一约定）。
 *
 * <p>不限定 {@code markerInterface}：{@code NoticeUserMapper} 是一条只读快照查询，
 * 没有对应实体，也就不继承 {@code BaseMapper}。
 */
@Configuration
@MapperScan(basePackages = "com.lautung.oa.notice.mapper")
public class NoticeMapperConfiguration {
}
