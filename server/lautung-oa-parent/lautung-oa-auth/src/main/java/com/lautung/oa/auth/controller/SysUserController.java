package com.lautung.oa.auth.controller;


import com.lautung.oa.auth.datascope.DataScopeResolver;
import com.lautung.oa.auth.service.SysDeptService;
import com.lautung.oa.auth.service.SysPostService;
import com.lautung.oa.auth.service.SysUserExcelService;
import com.lautung.oa.auth.service.SysUserQueries;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.system.SysUserDetailVo;
import com.lautung.oa.vo.system.SysUserQueryVo;
import com.lautung.oa.vo.system.SysUserResetPasswordRequest;
import com.lautung.oa.vo.system.SysUserSaveRequest;
import com.lautung.oa.vo.system.SysUserUpdateRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author atguigu
 * @since 2023-02-02
 */
@Tag(name = "用户管理接口")
@RestController
@RequestMapping("/admin/system/sysUser")
public class SysUserController {

    @Autowired
    private SysUserService service;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataScopeResolver dataScopeResolver;

    @Autowired
    private SysDeptService sysDeptService;

    @Autowired
    private SysPostService sysPostService;

    @Autowired
    private SysUserExcelService sysUserExcelService;

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:edit')")
    @Operation(summary = "更新状态")
    @OperLog(title = "用户管理·更新用户状态", businessType = BusinessType.UPDATE)
    @PutMapping("updateStatus/{id}/{status}")
    public Result updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        service.updateStatus(id,status);
        return Result.ok();
    }

    //用户条件分页查询
    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser')")
    @Operation(summary = "用户条件分页查询")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,
                        @PathVariable Long limit,
                        SysUserQueryVo sysUserQueryVo) {
        //创建page对象
        Page<SysUser> pageParam = PageWindow.of(page, limit);

        // 关键字/状态/部门子树/DataScope 与导出共用同一组条件（#259）。
        List<Long> subtreeIds = sysUserQueryVo.getDeptId() == null
                ? List.of()
                : sysDeptService.findSubtreeIds(sysUserQueryVo.getDeptId());
        DataScope scope = dataScopeResolver.resolve(LoginUserInfoHelper.getUserId());
        LambdaQueryWrapper<SysUser> wrapper = SysUserQueries.wrapper(
                sysUserQueryVo, subtreeIds, scope, false);

        //调用mp的方法实现条件分页查询
        IPage<SysUser> pageModel = service.page(pageParam, wrapper);
        return Result.ok(pageModel);
    }

    /**
     * 导出（#259）：按与列表相同的筛选与 DataScope 流式写 xlsx。
     * 导出是 GET 但会一次带走大量行，因此额外要求独立按钮点 {@code system:user:export}；
     * 仍然只读，不产生服务端状态变更。
     */
    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:export')")
    @Operation(summary = "导出用户")
    @OperLog(title = "用户·导出", businessType = BusinessType.OTHER)
    @GetMapping("export")
    public void export(SysUserQueryVo sysUserQueryVo, HttpServletResponse response) throws IOException {
        DataScope scope = dataScopeResolver.resolve(LoginUserInfoHelper.getUserId());
        String filename = "sys-user-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ".xlsx";
        response.setContentType(SysUserExcelService.XLSX_CONTENT_TYPE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        sysUserExcelService.export(sysUserQueryVo, scope, response.getOutputStream());
    }

    /**
     * 导入模板下载（#259）：只读空模板，跟用户页同一个页面授予；真正写库的 import
     * 另要独立按钮点 {@code system:user:import}。
     */
    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser')")
    @Operation(summary = "下载用户导入模板")
    @GetMapping("importTemplate")
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType(SysUserExcelService.XLSX_CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename=\"user-import-template.xlsx\"");
        sysUserExcelService.writeTemplate(response.getOutputStream());
    }

    /**
     * 逐行导入（#259/#263）：初始密码必填（6–100 位），按提交值 BCrypt 落库；失败行不写、
     * 成功与失败行号/原因汇总返回，不做全量回滚；已有用户名一律按「用户名已存在」拒绝，不覆盖。
     * 密码是动作参数而不是数据列，响应不回显，操作日志按参数名打码。
     */
    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:import')")
    @Operation(summary = "导入用户")
    @OperLog(title = "用户·导入", businessType = BusinessType.CREATE)
    @PostMapping("import")
    public Result importUsers(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "initialPassword", required = false) String initialPassword) {
        return Result.ok(sysUserExcelService.importUsers(file, initialPassword));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser')")
    @Operation(summary = "获取用户")
    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id) {
        SysUserDetailVo detail = service.findDetail(id);
        return Result.ok(detail);
    }

    /**
     * 用户页左侧只读部门树（#249）：只判 {@code /system/sysUser} 页面授予，
     * 不要求 {@code /system/sysDept}——部门维护入口仍在部门管理页，这里没有 CRUD。
     */
    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser')")
    @Operation(summary = "用户页部门树（只读）")
    @GetMapping("depts")
    public Result depts() {
        return Result.ok(sysDeptService.findTree());
    }

    /**
     * 用户页表单的只读岗位选项（#249）：同样只判用户页授予；
     * 只返回启用岗位，停用岗位不会出现在选择器里。
     */
    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser')")
    @Operation(summary = "用户页岗位选项（只读）")
    @GetMapping("posts")
    public Result posts() {
        return Result.ok(sysPostService.findEnabledOptions());
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:add')")
    @Operation(summary = "保存用户")
    @OperLog(title = "用户管理·新增用户", businessType = BusinessType.CREATE)
    @PostMapping("save")
    public Result save(@Valid @RequestBody SysUserSaveRequest request) {
        SysUser user = request.toEntity();
        // 密码必填由 DTO 保证；提交的明文一律编码后落库，不再有把空串写进密码列的分支（ADR 0034）。
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // 岗位与用户同一事务（#248）：非法岗位集合不留下半个用户。
        service.saveUser(user, request.getPostIds());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:edit')")
    @Operation(summary = "更新用户")
    @OperLog(title = "用户管理·修改用户", businessType = BusinessType.UPDATE)
    @PutMapping("update")
    public Result updateById(@Valid @RequestBody SysUserUpdateRequest request) {
        SysUser user = request.toEntity();
        SysUser existing = service.getById(user.getId());
        if (existing == null) {
            // Nothing to update; never write an unmasked request password to a row that shows up later.
            return Result.ok();
        }
        String submittedPassword = user.getPassword();
        boolean passwordChanged = StringUtils.hasText(submittedPassword)
                && !passwordEncoder.matches(submittedPassword, existing.getPassword());
        // 密码列只由 updateUser 事务内的 changePassword 写；部门/岗位校验先于改密，
        // 非法归属不会留下已改的密码与作废的会话（#260 二轮）。update_time 由公共字段
        // 处理器填（ADR 0031），updateById 不会因空更新失败。
        String encodedPassword = passwordChanged ? passwordEncoder.encode(submittedPassword) : null;
        user.setPassword(null);
        // postIds 未传保留、空列表清空；同步与用户行更新同一事务（#248）。
        service.updateUser(user, request.getPostIds(), encodedPassword);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:resetPwd')")
    @Operation(summary = "重置密码")
    @OperLog(title = "用户·重置密码", businessType = BusinessType.UPDATE)
    @PostMapping("{id}/resetPassword")
    public Result resetPassword(@PathVariable Long id,
                                @Valid @RequestBody SysUserResetPasswordRequest request) {
        // 与 updateById 的缺失语义一致：目标行不存在时静默成功，不编码、不写库。
        if (service.getById(id) == null) {
            return Result.ok();
        }
        // 复用改密语句：新哈希与 token_version + 1 在同一条 UPDATE 里落库，
        // 目标用户此前签发的全部会话立即失效（ADR 0029），操作者自己的令牌不受影响。
        service.changePassword(id, passwordEncoder.encode(request.getNewPassword()));
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:remove')")
    @Operation(summary = "删除用户")
    @OperLog(title = "用户管理·删除用户", businessType = BusinessType.DELETE)
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        service.removeById(id);
        return Result.ok();
    }

}

