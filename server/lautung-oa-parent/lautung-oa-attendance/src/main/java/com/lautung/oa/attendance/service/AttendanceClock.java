package com.lautung.oa.attendance.service;

import java.time.LocalDateTime;

/**
 * 考勤业务时钟端口（#281）：推导的唯一时间来源，生产走服务器本地时间，
 * 测试注入固定/可推进实现，推导不依赖 {@code LocalDateTime.now()}。
 *
 * <p>独立类型而不是直接注入 {@code java.time.Clock}：登录限流等模块已占用
 * {@code Clock} bean（且测试里带 @Primary），共用会让业务日期被登录时钟的
 * 时区/固定值带偏。
 */
@FunctionalInterface
public interface AttendanceClock {

    /** 当前服务器本地业务时间。 */
    LocalDateTime now();
}
