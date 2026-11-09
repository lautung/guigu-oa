package com.lautung.oa.attendance.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * attendance 域的 Mapper 注册：扫描范围随模块走（与 auth/dict/monitor/config 同一约定），
 * 只扫本域，不把别的域的 Mapper 拉进来。
 */
@Configuration
@MapperScan(basePackages = "com.lautung.oa.attendance.mapper", markerInterface = BaseMapper.class)
public class AttendanceMapperConfiguration {
}
