package com.lautung.oa.attendance.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.attendance.service.AttendanceClock;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * attendance 域的 Mapper 注册：扫描范围随模块走（与 auth/dict/monitor/config 同一约定），
 * 只扫本域，不把别的域的 Mapper 拉进来。
 *
 * <p>同时提供考勤业务时钟 bean（#281）：推导 service 只认 {@link AttendanceClock}，
 * 测试替换该类型即可固定/推进时间。
 */
@Configuration
@MapperScan(basePackages = "com.lautung.oa.attendance.mapper", markerInterface = BaseMapper.class)
public class AttendanceMapperConfiguration {

    @Bean
    public AttendanceClock attendanceClock() {
        return LocalDateTime::now;
    }
}
