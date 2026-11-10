package com.lautung.oa.attendance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.attendance.mapper.AttendanceUserMapper;
import com.lautung.oa.attendance.service.AttendanceLedgerService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.security.datascope.DataScopeProvider;
import com.lautung.oa.vo.attendance.AttendanceLedgerGrantRequest;
import com.lautung.oa.vo.attendance.AttendanceLedgerQueryVo;
import com.lautung.oa.vo.attendance.AttendanceLedgerVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 假期发放与流水（#280，ADR 0073）：页面 {@code /business/attendance/leave} 授予读，
 * 发放再判按钮点 {@code attendance:ledger:grant} 并记操作日志（ADR 0054）。
 *
 * <p>发放一次选多人、每人一行 append-only 流水（entry_type=1，签好 change_hours，
 * 负数即调整冲抵），数据范围按 {@code sys_user.dept_id} 当前 + 本人列
 * {@code attendance_leave_ledger.user_id} 五档（ADR 0053/0057）；成员候选与
 * 写入目标同档校验，不能靠 ID 绕过列表范围。
 */
@Tag(name = "假期发放接口")
@RestController
@RequestMapping("/admin/attendance/ledger")
public class AttendanceLedgerController {

    @Autowired
    private AttendanceLedgerService attendanceLedgerService;

    @Autowired
    private AttendanceUserMapper attendanceUserMapper;

    @Autowired
    private DataScopeProvider dataScopeProvider;

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/leave')")
    @Operation(summary = "发放流水分页（按数据范围，可按类型/日期筛选）")
    @GetMapping("{page}/{limit}")
    public Result pageQuery(@PathVariable Long page,
                            @PathVariable Long limit,
                            AttendanceLedgerQueryVo queryVo) {
        Page<AttendanceLedgerVo> pageParam = PageWindow.of(page, limit);
        return Result.ok(attendanceLedgerService.page(pageParam, queryVo, scope()));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/leave')")
    @Operation(summary = "发放成员候选（按数据范围）")
    @GetMapping("users")
    public Result users(@RequestParam(required = false) String name) {
        return Result.ok(attendanceUserMapper.selectScopedUsers(
                name == null || name.isBlank() ? null : name.trim(), null, scope()));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/leave','attendance:ledger:grant')")
    @Operation(summary = "发放/调整（一次多人，每人一行；负数冲抵）")
    @OperLog(title = "考勤·假期发放", businessType = BusinessType.CREATE)
    @PostMapping("grant")
    public Result grant(@Valid @RequestBody AttendanceLedgerGrantRequest request) {
        return Result.ok(attendanceLedgerService.grant(
                request, LoginUserInfoHelper.getUserId(), scope()));
    }

    private DataScope scope() {
        return dataScopeProvider.resolve(LoginUserInfoHelper.getUserId());
    }
}
