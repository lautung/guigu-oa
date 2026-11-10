package com.lautung.oa.attendance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.attendance.service.AttendancePunchService;
import com.lautung.oa.attendance.service.AttendanceRepairService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.security.datascope.DataScopeProvider;
import com.lautung.oa.vo.attendance.AttendancePunchQueryVo;
import com.lautung.oa.vo.attendance.AttendancePunchVo;
import com.lautung.oa.vo.attendance.AttendanceRepairRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 后台补卡处理（#281，ADR 0073）：页面 {@code /business/attendance/repair} 授予读，
 * 写再判按钮点 {@code attendance:record:repair} 并记操作日志（ADR 0054）。
 *
 * <p>数据范围（ADR 0053/0057）由 {@link DataScopeProvider} 解析成参数：
 * 列表复用打卡记录的查询（部门列 {@code sys_user.dept_id} 当前 + 本人列
 * {@code attendance_punch.user_id}）；成员候选与写入目标按 {@code sys_user.id}
 * 同档校验，读下钻与写补卡都不能靠 ID 绕过列表范围。
 */
@Tag(name = "补卡处理接口")
@RestController
@RequestMapping("/admin/attendance/repair")
public class AttendanceRepairController {

    @Autowired
    private AttendancePunchService attendancePunchService;

    @Autowired
    private AttendanceRepairService attendanceRepairService;

    @Autowired
    private DataScopeProvider dataScopeProvider;

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/repair')")
    @Operation(summary = "补卡页打卡流水分页（按数据范围）")
    @GetMapping("{page}/{limit}")
    public Result pageQuery(@PathVariable Long page,
                            @PathVariable Long limit,
                            AttendancePunchQueryVo queryVo) {
        Page<AttendancePunchVo> pageParam = PageWindow.of(page, limit);
        DataScope scope = scope();
        return Result.ok(attendancePunchService.page(pageParam, queryVo, scope));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/repair')")
    @Operation(summary = "补卡成员候选（按数据范围）")
    @GetMapping("users")
    public Result users(@RequestParam(required = false) String name) {
        return Result.ok(attendanceRepairService.users(name, scope()));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/repair')")
    @Operation(summary = "指定成员+日期的当前槽位状态（按数据范围）")
    @GetMapping("day")
    public Result day(@RequestParam Long userId,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(attendanceRepairService.day(userId, date, scope()));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/repair','attendance:record:repair')")
    @Operation(summary = "补卡（追加补卡流水，可只补一槽）")
    @OperLog(title = "考勤·补卡", businessType = BusinessType.UPDATE)
    @PostMapping("save")
    public Result save(@RequestBody AttendanceRepairRequest request) {
        return Result.ok(attendanceRepairService.repair(
                request, LoginUserInfoHelper.getUserId(), scope()));
    }

    private DataScope scope() {
        return dataScopeProvider.resolve(LoginUserInfoHelper.getUserId());
    }
}
