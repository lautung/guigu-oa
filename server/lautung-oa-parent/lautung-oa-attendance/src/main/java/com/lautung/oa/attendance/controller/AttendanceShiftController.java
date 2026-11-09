package com.lautung.oa.attendance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.attendance.service.AttendanceShiftService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.attendance.AttendanceShift;
import com.lautung.oa.vo.attendance.AttendanceShiftQueryVo;
import com.lautung.oa.vo.attendance.AttendanceShiftSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceShiftUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班次管理（#278，ADR 0073）：可建多个班次定义。班次/考勤组/节假日都是配置数据，
 * 不套数据范围（ADR 0052）；读写都判 {@code /business/attendance/group} 页面授予，
 * 写动作各挂 attendance:group:add|edit|remove 按钮点（班次与考勤组、节假日共用
 * 该页的三个按钮点，页面是同一个）。
 */
@Tag(name = "考勤班次接口")
@RestController
@RequestMapping("/admin/attendance/shift")
public class AttendanceShiftController {

    @Autowired
    private AttendanceShiftService attendanceShiftService;

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group')")
    @Operation(summary = "班次分页查询")
    @GetMapping("{page}/{limit}")
    public Result pageQuery(@PathVariable Long page,
                            @PathVariable Long limit,
                            AttendanceShiftQueryVo queryVo) {
        Page<AttendanceShift> pageParam = PageWindow.of(page, limit);
        LambdaQueryWrapper<AttendanceShift> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryVo.getName())) {
            wrapper.like(AttendanceShift::getName, queryVo.getName());
        }
        wrapper.orderByAsc(AttendanceShift::getId);
        IPage<AttendanceShift> pageModel = attendanceShiftService.page(pageParam, wrapper);
        return Result.ok(pageModel);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group')")
    @Operation(summary = "班次选项（考勤组表单下拉）")
    @GetMapping("options")
    public Result options() {
        List<AttendanceShift> options = attendanceShiftService.list(
                new LambdaQueryWrapper<AttendanceShift>().orderByAsc(AttendanceShift::getId));
        return Result.ok(options);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group','attendance:group:add')")
    @Operation(summary = "新增班次")
    @OperLog(title = "考勤·新增班次", businessType = BusinessType.CREATE)
    @PostMapping("save")
    public Result save(@Valid @RequestBody AttendanceShiftSaveRequest request) {
        attendanceShiftService.saveShift(request);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group','attendance:group:edit')")
    @Operation(summary = "修改班次")
    @OperLog(title = "考勤·修改班次", businessType = BusinessType.UPDATE)
    @PutMapping("update")
    public Result update(@Valid @RequestBody AttendanceShiftUpdateRequest request) {
        attendanceShiftService.updateShift(request);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group','attendance:group:remove')")
    @Operation(summary = "删除班次")
    @OperLog(title = "考勤·删除班次", businessType = BusinessType.DELETE)
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        attendanceShiftService.removeShift(id);
        return Result.ok();
    }
}
