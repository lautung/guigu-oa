package com.lautung.oa.auth.controller;


import com.lautung.oa.auth.datascope.DataScope;
import com.lautung.oa.auth.datascope.DataScopeResolver;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.system.AssginRoleVo;
import com.lautung.oa.vo.system.SysUserQueryVo;
import com.lautung.oa.vo.system.SysUserSaveRequest;
import com.lautung.oa.vo.system.SysUserUpdateRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

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

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:edit')")
    @Operation(summary = "更新状态")
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

        //封装条件，判断条件值不为空
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        //获取条件值
        String username = sysUserQueryVo.getKeyword();
        String createTimeBegin = sysUserQueryVo.getCreateTimeBegin();
        String createTimeEnd = sysUserQueryVo.getCreateTimeEnd();
        //判断条件值不为空
        //like 模糊查询
        if(!StringUtils.isEmpty(username)) {
            wrapper.like(SysUser::getUsername,username);
        }
        //ge 大于等于
        if(!StringUtils.isEmpty(createTimeBegin)) {
            wrapper.ge(SysUser::getCreateTime,createTimeBegin);
        }
        //le 小于等于
        if(!StringUtils.isEmpty(createTimeEnd)) {
            wrapper.le(SysUser::getCreateTime,createTimeEnd);
        }
        //数据范围（ADR 0057）：部门列 sys_user.dept_id，本人列 sys_user.id；空集是零行，不是全部。
        applyDataScope(wrapper, dataScopeResolver.resolve(LoginUserInfoHelper.getUserId()));

        //调用mp的方法实现条件分页查询
        IPage<SysUser> pageModel = service.page(pageParam, wrapper);
        return Result.ok(pageModel);
    }

    /**
     * 查询侧只接受参数（ADR 0053）：部门集用 wrapper 的 {@code in}，本人行用 {@code eq}，
     * 两者并集用 {@code or}；解析不出任何可见行时用常量 {@code 1 = 0} 得到零行。
     */
    private void applyDataScope(LambdaQueryWrapper<SysUser> wrapper, DataScope scope) {
        if (scope.isUnrestricted()) {
            return;
        }
        Set<Long> deptIds = scope.getDeptIds();
        Long selfUserId = scope.getSelfUserId();
        if (deptIds.isEmpty() && selfUserId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(nested -> {
            if (!deptIds.isEmpty()) {
                nested.in(SysUser::getDeptId, deptIds);
            }
            if (selfUserId != null) {
                if (!deptIds.isEmpty()) {
                    nested.or();
                }
                nested.eq(SysUser::getId, selfUserId);
            }
        });
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser')")
    @Operation(summary = "获取用户")
    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id) {
        SysUser user = service.getById(id);
        return Result.ok(user);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:add')")
    @Operation(summary = "保存用户")
    @PostMapping("save")
    public Result save(@Valid @RequestBody SysUserSaveRequest request) {
        SysUser user = request.toEntity();
        // 密码必填由 DTO 保证；提交的明文一律编码后落库，不再有把空串写进密码列的分支（ADR 0034）。
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        service.save(user);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:edit')")
    @Operation(summary = "更新用户")
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
        if (passwordChanged) {
            // The hash and the version bump land first; a later failure cannot leave old tokens alive.
            service.changePassword(existing.getId(), passwordEncoder.encode(submittedPassword));
        }
        // The password column is written only by changePassword; a profile edit must not carry an
        // old hash read before a concurrent change. update_time is filled by the common-field
        // handler (ADR 0031), which also keeps the row update non-empty.
        user.setPassword(null);
        service.updateById(user);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysUser','system:user:remove')")
    @Operation(summary = "删除用户")
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        service.removeById(id);
        return Result.ok();
    }

}

