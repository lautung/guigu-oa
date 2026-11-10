package com.lautung.oa.model.attendance;

/**
 * 逐日考勤状态（#281，ADR 0073 §统计口径）：读时推导的九种合法结果。
 *
 * <p>优先级：请假(类型) &gt; 缺卡 &gt; 迟到且早退 &gt; 迟到 &gt; 早退 &gt; 正常；
 * 非工作日显示休息日/休假日/补班日（当天有打卡也照常显示）。补班日按工作日公式
 * 是工作日：过了下班时刻仍缺槽位时落 {@link #MISSING}，只有「尚未到下班时刻且
 * 无任何打卡」的补班日保留 {@link #MAKEUP} 的日历提示。
 *
 * <p>未到下班时刻且缺槽位不是九枚举之一：推导结果用 {@code pending=true} 呈现
 * 「未打卡」，{@code status} 保持为空；未来日期同样不落状态。这样月统计只消费
 * 九枚举，不会把「还没到点」误记成缺卡。
 */
public final class AttendanceDayStatus {

    /** 正常。 */
    public static final int NORMAL = 1;
    /** 迟到。 */
    public static final int LATE = 2;
    /** 早退。 */
    public static final int EARLY_LEAVE = 3;
    /** 迟到且早退。 */
    public static final int LATE_AND_EARLY = 4;
    /** 缺卡。 */
    public static final int MISSING = 5;
    /** 请假（类型名由 {@code leaveTypeName} 带出）。 */
    public static final int LEAVE = 6;
    /** 休息日。 */
    public static final int REST = 7;
    /** 休假日。 */
    public static final int HOLIDAY = 8;
    /** 补班日。 */
    public static final int MAKEUP = 9;

    private AttendanceDayStatus() {
    }
}
