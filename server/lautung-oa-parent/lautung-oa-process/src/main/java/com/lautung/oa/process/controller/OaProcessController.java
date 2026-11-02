package com.lautung.oa.process.controller;


import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.process.read.ApprovalListReader;
import com.lautung.oa.vo.process.ProcessQueryVo;
import com.lautung.oa.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 审批类型 前端控制器
 * </p>
 *
 * @author atguigu
 * @since 2023-02-14
 */
@RestController
@RequestMapping(value = "/admin/process")
public class OaProcessController {

    @Autowired
    private ApprovalListReader approvalListReader;

    //审批管理列表（配置实例查询：与办事列表同一读 module，#181）
    @PreAuthorize("@backofficeGate.isAllowed('/system/processMgr/process')")
    @Operation(summary = "获取分页列表")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,
                        @PathVariable Long limit,
                        ProcessQueryVo processQueryVo) {
        Page<ProcessVo> pageParam = PageWindow.of(page, limit);
        IPage<ProcessVo> pageModel =
                approvalListReader.instanceQuery(pageParam, processQueryVo);
        return Result.ok(pageModel);
    }
}
