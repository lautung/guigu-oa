package com.lautung.oa.attendance.service;

import com.lautung.oa.vo.attendance.AttendanceDayStatusVo;
import com.lautung.oa.vo.attendance.AttendanceMonthVo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 逐日考勤状态推导的唯一 service（#281，ADR 0073）：后台下钻、办事考勤记录页与
 * 月统计共用。只读事件流水/请假行，不落日报、不引定时任务。
 *
 * <p>九枚举优先级：请假(类型) &gt; 缺卡 &gt; 迟到且早退 &gt; 迟到 &gt; 早退 &gt; 正常；
 * 非工作日显示休息日/休假日/补班日。缺卡只在工作日且已过下班时刻后出现，未到
 * 下班时刻用 {@code pending=true} 呈现「未打卡」。
 */
public interface AttendanceDayStatusService {

    /** 单日推导；未加入考勤组时 hasGroup=false 且不落状态。 */
    AttendanceDayStatusVo day(Long userId, LocalDate date);

    /** 区间逐日推导（含首尾，日期升序）。 */
    List<AttendanceDayStatusVo> days(Long userId, LocalDate beginDate, LocalDate endDate);

    /**
     * 当月逐日推导（办事考勤记录页与后台下钻）。{@code month} 为 {@code yyyy-MM}，
     * 缺省取注入时钟的当前月，格式不合法 201「月份格式不正确」。
     */
    AttendanceMonthVo month(Long userId, String month);

    /**
     * 解析 {@code yyyy-MM} 月份（缺省取注入时钟的当前月，非法 201「月份格式不正确」）。
     * 月统计与推导共用同一个解析口径。
     */
    YearMonth resolveMonth(String month);

    /** 注入时钟的当前业务月，供页面默认值复用。 */
    YearMonth currentMonth();
}
