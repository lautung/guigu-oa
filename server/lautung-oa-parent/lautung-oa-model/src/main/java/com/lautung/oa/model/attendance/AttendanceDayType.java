package com.lautung.oa.model.attendance;

/**
 * 逐日状态类型（#279，ADR 0073）：工作日判定与页面展示共用一套编号。
 *
 * <p>工作日公式：{@code (星期 ∈ 组工作日 且 该日非休假日) 或 该日是补班日}。
 * 因此 {@link #WORKDAY} 与 {@link #MAKEUP} 是可打卡的两种类型；
 * {@link #NONE} 只用于「未加入考勤组」，不是一种排班。
 */
public final class AttendanceDayType {

    /** 未加入考勤组：没有排班，也不能打卡。 */
    public static final int NONE = 0;
    /** 工作日。 */
    public static final int WORKDAY = 1;
    /** 休息日。 */
    public static final int REST = 2;
    /** 休假日（全局节假日日历 day_type=1）。 */
    public static final int HOLIDAY = 3;
    /** 补班日（全局节假日日历 day_type=2）。 */
    public static final int MAKEUP = 4;

    private AttendanceDayType() {
    }

    public static boolean isWorkday(int dayType) {
        return dayType == WORKDAY || dayType == MAKEUP;
    }
}
