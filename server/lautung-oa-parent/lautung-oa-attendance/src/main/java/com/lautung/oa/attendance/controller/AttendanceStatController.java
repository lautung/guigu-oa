package com.lautung.oa.attendance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.attendance.service.AttendanceStatService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.security.datascope.DataScopeProvider;
import com.lautung.oa.vo.attendance.AttendanceMonthVo;
import com.lautung.oa.vo.attendance.AttendanceStatVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台月度考勤统计（#283，ADR 0073）：读端点只判页面
 * {@code /business/attendance/stat} 授予；数据范围由 {@link DataScopeProvider}
 * 解析成参数（ADR 0053/0057），部门列 = {@code sys_user.dept_id} 当前，
 * 本人列 = {@code sys_user.id}。下钻与列表同套范围，不能靠 userId 绕过。
 */
@Tag(name = "月度考勤统计接口")
@RestController
@RequestMapping("/admin/attendance/stat")
public class AttendanceStatController {

    @Autowired
    private AttendanceStatService attendanceStatService;

    @Autowired
    private DataScopeProvider dataScopeProvider;

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/stat')")
    @Operation(summary = "月度统计分页查询（按人汇总，数据范围五档）")
    @GetMapping("{page}/{limit}")
    public Result pageQuery(@PathVariable Long page,
                            @PathVariable Long limit,
                            @RequestParam(required = false) String month) {
        Page<AttendanceStatVo> pageParam = PageWindow.of(page, limit);
        DataScope scope = dataScopeProvider.resolve(LoginUserInfoHelper.getUserId());
        return Result.ok(attendanceStatService.page(pageParam, month, scope));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/stat')")
    @Operation(summary = "点人下钻当月逐日明细（复用日状态推导，同一数据范围）")
    @GetMapping("daily")
    public Result daily(@RequestParam(required = false) Long userId,
                        @RequestParam(required = false) String month) {
        DataScope scope = dataScopeProvider.resolve(LoginUserInfoHelper.getUserId());
        AttendanceMonthVo vo = attendanceStatService.month(userId, month, scope);
        return Result.ok(vo);
    }
}
