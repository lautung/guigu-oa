package com.lautung.oa.attendance.controller;

import com.lautung.oa.attendance.service.AttendanceDayStatusService;
import com.lautung.oa.attendance.service.AttendancePunchService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.model.attendance.AttendancePunch;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 办事打卡（#279，ADR 0073）：登录即有，不套后台页面授予与数据范围；
 * 端点构造上只读本人（用户 id 只从当前登录会话取，不接受请求参数）。
 * 办事写动作不记操作日志（ADR 0054：办事与审批写动作不重复记）。
 *
 * <p>#281 增「本人当月逐日状态」：与后台下钻、月统计复用同一推导 service。
 */
@Tag(name = "我的考勤接口")
@RestController
@RequestMapping("/admin/attendance/my")
public class AttendanceMyController {

    @Autowired
    private AttendancePunchService attendancePunchService;

    @Autowired
    private AttendanceDayStatusService attendanceDayStatusService;

    @Operation(summary = "今日打卡状态（班次/工作日判定/两卡状态）")
    @GetMapping("today")
    public Result today() {
        return Result.ok(attendancePunchService.today(LoginUserInfoHelper.getUserId()));
    }

    @Operation(summary = "本人当月逐日考勤状态（缺省当前月）")
    @GetMapping("records")
    public Result records(@RequestParam(required = false) String month) {
        return Result.ok(attendanceDayStatusService.month(
                LoginUserInfoHelper.getUserId(), month));
    }

    @Operation(summary = "上班打卡（服务器时间，可重复打）")
    @PostMapping("punch/in")
    public Result punchIn() {
        return Result.ok(attendancePunchService.punch(
                LoginUserInfoHelper.getUserId(), AttendancePunch.SLOT_IN));
    }

    @Operation(summary = "下班打卡（服务器时间，可重复打）")
    @PostMapping("punch/out")
    public Result punchOut() {
        return Result.ok(attendancePunchService.punch(
                LoginUserInfoHelper.getUserId(), AttendancePunch.SLOT_OUT));
    }
}
