package com.lautung.oa.attendance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.attendance.service.AttendancePunchService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.security.datascope.DataScopeProvider;
import com.lautung.oa.vo.attendance.AttendancePunchQueryVo;
import com.lautung.oa.vo.attendance.AttendancePunchVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台打卡记录（#279，ADR 0073）：读端点只判页面 {@code /business/attendance/record}
 * 授予；数据范围由 {@link DataScopeProvider} 解析成参数（ADR 0053/0057），
 * 部门列 = {@code sys_user.dept_id} 当前部门，本人列 = {@code attendance_punch.user_id}。
 */
@Tag(name = "打卡记录接口")
@RestController
@RequestMapping("/admin/attendance/punch")
public class AttendancePunchController {

    @Autowired
    private AttendancePunchService attendancePunchService;

    @Autowired
    private DataScopeProvider dataScopeProvider;

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/record')")
    @Operation(summary = "打卡记录分页查询（join 姓名/部门，按数据范围）")
    @GetMapping("{page}/{limit}")
    public Result pageQuery(@PathVariable Long page,
                            @PathVariable Long limit,
                            AttendancePunchQueryVo queryVo) {
        Page<AttendancePunchVo> pageParam = PageWindow.of(page, limit);
        DataScope scope = dataScopeProvider.resolve(LoginUserInfoHelper.getUserId());
        return Result.ok(attendancePunchService.page(pageParam, queryVo, scope));
    }
}
