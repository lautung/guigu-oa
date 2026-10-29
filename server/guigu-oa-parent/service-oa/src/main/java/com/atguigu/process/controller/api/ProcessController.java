package com.atguigu.process.controller.api;

import com.atguigu.auth.service.SysUserService;
import com.atguigu.common.page.PageWindow;
import com.atguigu.common.result.Result;
import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessType;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.ApprovalTemplateSupport;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.process.service.OaProcessTemplateService;
import com.atguigu.process.cc.CarbonCopyService;
import com.atguigu.process.cc.CarbonCopyRecipient;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessVo;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "审批流管理")
@RestController
@RequestMapping(value="/admin/process")
@CrossOrigin //跨域
public class ProcessController {

    @Autowired
    private OaProcessTemplateService processTemplateService;

    @Autowired
    private OaProcessService processService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private CarbonCopyService carbonCopyService;

    @Operation(summary = "待处理")
    @GetMapping("/findPending/{page}/{limit}")
    public Result findPending(
            @Parameter(name = "page", description = "当前页码", required = true)
            @PathVariable Long page,
            @Parameter(name = "limit", description = "每页记录数", required = true)
            @PathVariable Long limit) {
        Page<Process> pageParam = PageWindow.of(page, limit);
        IPage<ProcessVo> pageModel = processService.findPending(currentUser(), pageParam);
        return Result.ok(pageModel);
    }

    @Operation(summary = "启动流程")
    @PostMapping("/startUp")
    public Result startUp(@RequestBody ProcessFormVo processFormVo) {
        processService.startUp(currentUser(), processFormVo);
        return Result.ok();
    }

    //获取审批模板数据（办事发起页专用：只暴露已发布模板，未发布模板仍由配置侧按授予读取）
    @GetMapping("getProcessTemplate/{processTemplateId}")
    public Result getProcessTemplate(@PathVariable Long processTemplateId) {
        ProcessTemplate processTemplate = processTemplateService.read(processTemplateId);
        if (processTemplate == null
                || !ApprovalTemplateSupport.canInitiate(processTemplate.getStatus())) {
            return Result.ok(null);
        }
        return Result.ok(processTemplate);
    }

    @GetMapping("findStartable")
    public Result findStartable() {
        List<ProcessType> list = processTemplateService.findStartable();
        return Result.ok(list);
    }

    //查看审批详情信息
    @GetMapping("show/{id}")
    public Result show(@PathVariable Long id) {
        Map<String,Object> map = processService.show(currentUser(), id);
        return Result.ok(map);
    }

    //审批
    @Operation(summary = "审批")
    @PostMapping("approve")
    public Result approve(@RequestBody ApprovalVo approvalVo) {
        processService.approve(currentUser(), approvalVo);
        return Result.ok();
    }

    @Operation(summary = "已处理")
    @GetMapping("/findProcessed/{page}/{limit}")
    public Result findProcessed(
            @Parameter(name = "page", description = "当前页码", required = true)
            @PathVariable Long page,
            @Parameter(name = "limit", description = "每页记录数", required = true)
            @PathVariable Long limit) {
        Page<Process> pageParam = PageWindow.of(page, limit);
        IPage<ProcessVo> pageModel = processService.findProcessed(currentUser(), pageParam);
        return Result.ok(pageModel);
    }

    @Operation(summary = "抄送我的")
    @GetMapping("/findCarbonCopied/{page}/{limit}")
    public Result findCarbonCopied(
            @Parameter(name = "page", description = "当前页码", required = true)
            @PathVariable Long page,
            @Parameter(name = "limit", description = "每页记录数", required = true)
            @PathVariable Long limit) {
        Page<ProcessVo> pageParam = PageWindow.of(page, limit);
        return Result.ok(carbonCopyService.findMine(pageParam, currentUser().getId()));
    }

    @Operation(summary = "发起抄送人选项")
    @GetMapping("/carbonCopyRecipients")
    public Result carbonCopyRecipients() {
        List<CarbonCopyRecipient> recipients = carbonCopyService.findRecipients(currentUser().getId());
        return Result.ok(recipients);
    }

    @Operation(summary = "已发起")
    @GetMapping("/findStarted/{page}/{limit}")
    public Result findStarted(
            @Parameter(name = "page", description = "当前页码", required = true)
            @PathVariable Long page,
            @Parameter(name = "limit", description = "每页记录数", required = true)
            @PathVariable Long limit) {
        Page<ProcessVo> pageParam = PageWindow.of(page, limit);
        IPage<ProcessVo> pageModel = processService.findStarted(currentUser(), pageParam);
        return Result.ok(pageModel);
    }

    @GetMapping("getCurrentUser")
    public Result getCurrentUser() {
        Map<String,Object> map = sysUserService.getCurrentUser();
        return Result.ok(map);
    }

    private SysUser currentUser() {
        return sysUserService.getById(LoginUserInfoHelper.getUserId());
    }
}
