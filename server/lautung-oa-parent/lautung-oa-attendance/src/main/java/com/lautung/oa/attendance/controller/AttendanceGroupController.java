package com.lautung.oa.attendance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.attendance.mapper.AttendanceGroupMapper;
import com.lautung.oa.attendance.mapper.AttendanceUserMapper;
import com.lautung.oa.attendance.service.AttendanceGroupService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.attendance.AttendanceGroupQueryVo;
import com.lautung.oa.vo.attendance.AttendanceGroupSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceGroupUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceGroupVo;
import com.lautung.oa.vo.system.SysUserOptionVo;
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
 * 考勤组管理（#278，ADR 0073）：绑一个班次、每周工作日多选、成员直接选人。
 * 成员替换走组表单整体提交（save 建组带成员、update 显式列表整体替换）；
 * 成员候选查询走 attendance 自有 mapper 读 sys_user（只列启用用户，不依赖
 * auth 与用户管理页权限，形状照 userOptions 投影）。
 */
@Tag(name = "考勤组接口")
@RestController
@RequestMapping("/admin/attendance/group")
public class AttendanceGroupController {

    @Autowired
    private AttendanceGroupMapper attendanceGroupMapper;

    @Autowired
    private AttendanceGroupService attendanceGroupService;

    @Autowired
    private AttendanceUserMapper attendanceUserMapper;

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group')")
    @Operation(summary = "考勤组分页查询（join 班次名称与成员数）")
    @GetMapping("{page}/{limit}")
    public Result pageQuery(@PathVariable Long page,
                            @PathVariable Long limit,
                            AttendanceGroupQueryVo queryVo) {
        Page<AttendanceGroupVo> pageParam = PageWindow.of(page, limit);
        IPage<AttendanceGroupVo> pageModel = attendanceGroupMapper.selectGroupPage(pageParam,
                StringUtils.hasText(queryVo.getName()) ? queryVo.getName() : null);
        return Result.ok(pageModel);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group')")
    @Operation(summary = "考勤组成员列表")
    @GetMapping("{id}/members")
    public Result members(@PathVariable Long id) {
        return Result.ok(attendanceGroupService.findMembers(id));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group')")
    @Operation(summary = "成员候选：启用用户选项")
    @GetMapping("userOptions")
    public Result userOptions() {
        List<SysUser> users = attendanceUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getUsername)
                .orderByAsc(SysUser::getId));
        return Result.ok(users.stream()
                .map(user -> new SysUserOptionVo(user.getId(), user.getUsername(), user.getName()))
                .toList());
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group','attendance:group:add')")
    @Operation(summary = "新增考勤组")
    @OperLog(title = "考勤·新增考勤组", businessType = BusinessType.CREATE)
    @PostMapping("save")
    public Result save(@Valid @RequestBody AttendanceGroupSaveRequest request) {
        attendanceGroupService.saveGroup(request);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group','attendance:group:edit')")
    @Operation(summary = "修改考勤组（成员显式列表整体替换）")
    @OperLog(title = "考勤·修改考勤组", businessType = BusinessType.UPDATE)
    @PutMapping("update")
    public Result update(@Valid @RequestBody AttendanceGroupUpdateRequest request) {
        attendanceGroupService.updateGroup(request);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/business/attendance/group','attendance:group:remove')")
    @Operation(summary = "删除考勤组（成员关系行硬删）")
    @OperLog(title = "考勤·删除考勤组", businessType = BusinessType.DELETE)
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        attendanceGroupService.removeGroup(id);
        return Result.ok();
    }
}
