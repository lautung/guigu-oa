package com.lautung.oa.attendance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.attendance.service.AttendanceHolidayService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.attendance.AttendanceHoliday;
import com.lautung.oa.vo.attendance.AttendanceHolidayQueryVo;
import com.lautung.oa.vo.attendance.AttendanceHolidaySaveRequest;
import com.lautung.oa.vo.attendance.AttendanceHolidayUpdateRequest;
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

/**
 * 全局节假日维护（#278，ADR 0073）：日期唯一、1 休假日 / 2 补班日，硬删。
 * 维护入口在「考勤组与班次」页的节假日区块，读写共用该页的页面授予与
 * attendance:group:add|edit|remove 按钮点。
 */
@Tag(name = "考勤节假日接口")
@RestController
@RequestMapping("/admin/attendance/holiday")
public class AttendanceHolidayController {

    @Autowired
    private AttendanceHolidayService attendanceHolidayService;

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group')")
    @Operation(summary = "节假日分页查询")
    @GetMapping("{page}/{limit}")
    public Result pageQuery(@PathVariable Long page,
                            @PathVariable Long limit,
                            AttendanceHolidayQueryVo queryVo) {
        Page<AttendanceHoliday> pageParam = PageWindow.of(page, limit);
        LambdaQueryWrapper<AttendanceHoliday> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryVo.getName())) {
            wrapper.like(AttendanceHoliday::getName, queryVo.getName());
        }
        if (queryVo.getDayType() != null) {
            wrapper.eq(AttendanceHoliday::getDayType, queryVo.getDayType());
        }
        wrapper.orderByDesc(AttendanceHoliday::getHolidayDate);
        IPage<AttendanceHoliday> pageModel = attendanceHolidayService.page(pageParam, wrapper);
        return Result.ok(pageModel);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group','attendance:group:add')")
    @Operation(summary = "新增节假日")
    @OperLog(title = "考勤·新增节假日", businessType = BusinessType.CREATE)
    @PostMapping("save")
    public Result save(@Valid @RequestBody AttendanceHolidaySaveRequest request) {
        attendanceHolidayService.saveHoliday(request);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group','attendance:group:edit')")
    @Operation(summary = "修改节假日")
    @OperLog(title = "考勤·修改节假日", businessType = BusinessType.UPDATE)
    @PutMapping("update")
    public Result update(@Valid @RequestBody AttendanceHolidayUpdateRequest request) {
        attendanceHolidayService.updateHoliday(request);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group','attendance:group:remove')")
    @Operation(summary = "删除节假日（硬删）")
    @OperLog(title = "考勤·删除节假日", businessType = BusinessType.DELETE)
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        attendanceHolidayService.removeHoliday(id);
        return Result.ok();
    }
}
