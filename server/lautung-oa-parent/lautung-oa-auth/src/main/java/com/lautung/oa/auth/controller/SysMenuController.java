package com.lautung.oa.auth.controller;


import com.lautung.oa.auth.service.SysMenuService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.model.system.SysMenu;
import com.lautung.oa.vo.system.AssginMenuVo;
import com.lautung.oa.vo.system.SysMenuSaveRequest;
import com.lautung.oa.vo.system.SysMenuUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 菜单表 前端控制器
 * </p>
 *
 * @author atguigu
 * @since 2023-02-02
 */
@Tag(name = "菜单管理接口")
@RestController
@RequestMapping("/admin/system/sysMenu")
public class SysMenuController {

    @Autowired
    private SysMenuService sysMenuService;

    //查询所有菜单和角色分配的菜单
    @PreAuthorize("@backofficeGate.isAllowed('/system/sysRole')")
    @Operation(summary = "查询所有菜单和角色分配的菜单")
    @GetMapping("toAssign/{roleId}")
    public Result toAssign(@PathVariable Long roleId) {
        List<SysMenu> list = sysMenuService.findMenuByRoleId(roleId);
        return Result.ok(list);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysRole','system:role:assignAuth')")
    @Operation(summary = "角色分配菜单")
    @PostMapping("/doAssign")
    public Result doAssign(@Valid @RequestBody AssginMenuVo assginMenuVo) {
        sysMenuService.doAssign(assginMenuVo);
        return Result.ok();
    }

    //菜单列表接口
    @PreAuthorize("@backofficeGate.isAllowed('/system/sysMenu')")
    @Operation(summary = "菜单列表")
    @GetMapping("findNodes")
    public Result findNodes() {

        List<SysMenu> list = sysMenuService.findNodes();
        return Result.ok(list);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysMenu','system:menu:add')")
    @Operation(summary = "新增菜单")
    @PostMapping("save")
    public Result save(@Valid @RequestBody SysMenuSaveRequest request) {
        sysMenuService.saveMenu(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysMenu','system:menu:edit')")
    @Operation(summary = "修改菜单")
    @PutMapping("update")
    public Result updateById(@Valid @RequestBody SysMenuUpdateRequest request) {
        sysMenuService.updateMenu(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysMenu','system:menu:remove')")
    @Operation(summary = "删除菜单")
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        sysMenuService.removeMenuById(id);
        return Result.ok();
    }

}

