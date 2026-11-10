package com.lautung.oa.model.attendance;

/**
 * 考勤域的业务键（ADR 0067 值域）：请假单与加班单各一个，供
 * {@code BusinessApprovalStarter/Callback/Reader} 使用。
 */
public final class AttendanceBusinessTypes {

    public static final String LEAVE = "attendance.leave";

    public static final String OVERTIME = "attendance.overtime";

    private AttendanceBusinessTypes() {
    }
}
