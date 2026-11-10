package com.lautung.oa.attendance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.attendance.service.AttendanceLeaveService;
import com.lautung.oa.attendance.service.AttendanceOvertimeService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.attendance.AttendanceApplySubmitRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveVo;
import com.lautung.oa.vo.attendance.AttendanceOvertimeSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceOvertimeUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceOvertimeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 办事「我的申请」（#282，ADR 0073/0067）：请假单与加班单两页签的列表、详情、
 * 草稿新建/修改/删除与提交。登录即有，不套后台页面授予与数据范围；用户 id 只从
 * 当前登录会话取，构造上只读本人。办事写动作不记操作日志（ADR 0054）。
 *
 * <p>提交只带客户端幂等键；天数/小时数由服务端定稿。审批详情不在这里展开：
 * 单据详情附最近一条审批摘要，点进审批详情走现有详情页与成员资格 gate。
 */
@Tag(name = "我的申请接口")
@RestController
@RequestMapping("/admin/attendance/my")
public class AttendanceMyApplicationController {

    private final AttendanceLeaveService leaveService;

    private final AttendanceOvertimeService overtimeService;

    public AttendanceMyApplicationController(AttendanceLeaveService leaveService,
                                             AttendanceOvertimeService overtimeService) {
        this.leaveService = leaveService;
        this.overtimeService = overtimeService;
    }

    // ---------------- 请假单 ----------------

    @Operation(summary = "我的请假单分页（只读本人，可按状态筛选）")
    @GetMapping("leave/{page}/{limit}")
    public Result leavePage(@PathVariable Long page, @PathVariable Long limit,
                            AttendanceApplyQueryVo queryVo) {
        Page<AttendanceLeaveVo> pageParam = PageWindow.of(page, limit);
        return Result.ok(leaveService.page(pageParam, currentUserId(), queryVo));
    }

    @Operation(summary = "我的请假单详情（单据 + 最近一条审批摘要）")
    @GetMapping("leave/{id}")
    public Result leaveDetail(@PathVariable Long id) {
        return Result.ok(leaveService.detail(currentUserId(), id));
    }

    @Operation(summary = "我的请假类型选项（启用类型，需要余额的带可用余额）")
    @GetMapping("leave/types")
    public Result leaveTypes() {
        return Result.ok(leaveService.leaveTypes(currentUserId()));
    }

    @Operation(summary = "新建请假草稿")
    @PostMapping("leave/save")
    public Result saveLeave(@Valid @RequestBody AttendanceLeaveSaveRequest request) {
        return Result.ok(leaveService.saveDraft(currentUserId(), request));
    }

    @Operation(summary = "修改请假单（草稿/拒绝/撤回）")
    @PutMapping("leave/update")
    public Result updateLeave(@Valid @RequestBody AttendanceLeaveUpdateRequest request) {
        return Result.ok(leaveService.updateDraft(currentUserId(), request));
    }

    @Operation(summary = "删除请假单（审批中与已通过不可删）")
    @DeleteMapping("leave/remove/{id}")
    public Result removeLeave(@PathVariable Long id) {
        leaveService.removeDraft(currentUserId(), id);
        return Result.ok(null);
    }

    @Operation(summary = "提交请假单（状态 CAS + 起审批同一事务）")
    @PostMapping("leave/{id}/submit")
    public Result submitLeave(@PathVariable Long id,
                              @Valid @RequestBody AttendanceApplySubmitRequest request) {
        return Result.ok(leaveService.submit(
                currentUserId(), id, request.getIdempotencyKey()));
    }

    // ---------------- 加班单 ----------------

    @Operation(summary = "我的加班单分页（只读本人，可按状态筛选）")
    @GetMapping("overtime/{page}/{limit}")
    public Result overtimePage(@PathVariable Long page, @PathVariable Long limit,
                               AttendanceApplyQueryVo queryVo) {
        Page<AttendanceOvertimeVo> pageParam = PageWindow.of(page, limit);
        return Result.ok(overtimeService.page(pageParam, currentUserId(), queryVo));
    }

    @Operation(summary = "我的加班单详情（单据 + 最近一条审批摘要）")
    @GetMapping("overtime/{id}")
    public Result overtimeDetail(@PathVariable Long id) {
        return Result.ok(overtimeService.detail(currentUserId(), id));
    }

    @Operation(summary = "新建加班草稿")
    @PostMapping("overtime/save")
    public Result saveOvertime(@Valid @RequestBody AttendanceOvertimeSaveRequest request) {
        return Result.ok(overtimeService.saveDraft(currentUserId(), request));
    }

    @Operation(summary = "修改加班单（草稿/拒绝/撤回）")
    @PutMapping("overtime/update")
    public Result updateOvertime(@Valid @RequestBody AttendanceOvertimeUpdateRequest request) {
        return Result.ok(overtimeService.updateDraft(currentUserId(), request));
    }

    @Operation(summary = "删除加班单（审批中与已通过不可删）")
    @DeleteMapping("overtime/remove/{id}")
    public Result removeOvertime(@PathVariable Long id) {
        overtimeService.removeDraft(currentUserId(), id);
        return Result.ok(null);
    }

    @Operation(summary = "提交加班单（状态 CAS + 起审批同一事务）")
    @PostMapping("overtime/{id}/submit")
    public Result submitOvertime(@PathVariable Long id,
                                 @Valid @RequestBody AttendanceApplySubmitRequest request) {
        return Result.ok(overtimeService.submit(
                currentUserId(), id, request.getIdempotencyKey()));
    }

    private Long currentUserId() {
        return LoginUserInfoHelper.getUserId();
    }
}
