package com.lautung.oa.attendance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.attendance.service.AttendanceLeaveTypeService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.attendance.AttendanceLeaveType;
import com.lautung.oa.vo.attendance.AttendanceLeaveTypeQueryVo;
import com.lautung.oa.vo.attendance.AttendanceLeaveTypeSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveTypeUpdateRequest;
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
 * 假期类型管理（#280，ADR 0073）：配置数据不套数据范围（ADR 0052）；
 * 读写都判 {@code /business/attendance/leave} 页面授予，写动作分别挂
 * attendance:type:add|edit|remove 按钮点并记操作日志（ADR 0054）。
 *
 * <p>类型被流水/请假单引用后删除返回 201「假期类型已被引用，只能停用」；
 * 停用走修改端点（status=0），历史流水照读。
 */
@Tag(name = "假期类型接口")
@RestController
@RequestMapping("/admin/attendance/leave-type")
public class AttendanceLeaveTypeController {

    @Autowired
    private AttendanceLeaveTypeService attendanceLeaveTypeService;

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/leave')")
    @Operation(summary = "假期类型分页查询")
    @GetMapping("{page}/{limit}")
    public Result pageQuery(@PathVariable Long page,
                            @PathVariable Long limit,
                            AttendanceLeaveTypeQueryVo queryVo) {
        Page<AttendanceLeaveType> pageParam = PageWindow.of(page, limit);
        LambdaQueryWrapper<AttendanceLeaveType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryVo.getName())) {
            wrapper.like(AttendanceLeaveType::getName, queryVo.getName().trim());
        }
        if (queryVo.getStatus() != null) {
            wrapper.eq(AttendanceLeaveType::getStatus, queryVo.getStatus());
        }
        wrapper.orderByAsc(AttendanceLeaveType::getSortNo).orderByAsc(AttendanceLeaveType::getId);
        IPage<AttendanceLeaveType> pageModel = attendanceLeaveTypeService.page(pageParam, wrapper);
        return Result.ok(pageModel);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/leave')")
    @Operation(summary = "假期类型选项（发放/筛选下拉；停用类型也返回）")
    @GetMapping("options")
    public Result options() {
        List<AttendanceLeaveType> options = attendanceLeaveTypeService.list(
                new LambdaQueryWrapper<AttendanceLeaveType>()
                        .orderByAsc(AttendanceLeaveType::getSortNo)
                        .orderByAsc(AttendanceLeaveType::getId));
        return Result.ok(options);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/leave','attendance:type:add')")
    @Operation(summary = "新增假期类型")
    @OperLog(title = "考勤·新增假期类型", businessType = BusinessType.CREATE)
    @PostMapping("save")
    public Result save(@Valid @RequestBody AttendanceLeaveTypeSaveRequest request) {
        attendanceLeaveTypeService.saveType(request);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/leave','attendance:type:edit')")
    @Operation(summary = "修改假期类型（完整表单覆盖，含启停用）")
    @OperLog(title = "考勤·修改假期类型", businessType = BusinessType.UPDATE)
    @PutMapping("update")
    public Result update(@Valid @RequestBody AttendanceLeaveTypeUpdateRequest request) {
        attendanceLeaveTypeService.updateType(request);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/leave','attendance:type:remove')")
    @Operation(summary = "删除假期类型（有引用历史时只能停用）")
    @OperLog(title = "考勤·删除假期类型", businessType = BusinessType.DELETE)
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        attendanceLeaveTypeService.removeType(id);
        return Result.ok();
    }
}
