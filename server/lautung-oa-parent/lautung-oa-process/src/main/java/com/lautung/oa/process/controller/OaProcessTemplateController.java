package com.lautung.oa.process.controller;


import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.process.service.OaProcessTemplateService;
import com.lautung.oa.vo.process.ProcessTemplateSaveRequest;
import com.lautung.oa.vo.process.ProcessTemplateUpdateRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 审批模板 前端控制器
 * </p>
 *
 * @author atguigu
 * @since 2023-02-14
 */
@RestController
@RequestMapping(value = "/admin/process/processTemplate")
public class OaProcessTemplateController {

    @Autowired
    private OaProcessTemplateService processTemplateService;

    //分页查询审批模板
    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processTemplate')")
    @Operation(summary = "获取分页审批模板数据")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,
                        @PathVariable Long limit) {
        Page<ProcessTemplate> pageParam = PageWindow.of(page, limit);
        //分页查询审批模板，把审批类型对应名称查询
        IPage<ProcessTemplate> pageModel =
                processTemplateService.findPage(pageParam);
        return Result.ok(pageModel);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processTemplate')")
    @Operation(summary = "获取")
    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id) {
        ProcessTemplate processTemplate = processTemplateService.read(id);
        return Result.ok(processTemplate);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processTemplate','process:template:add')")
    @Operation(summary = "新增")
    @PostMapping("save")
    public Result save(@Valid @RequestBody ProcessTemplateSaveRequest request) {
        // save 只创建：请求体不给主键，改已有模板走 update（ADR 0034）。
        processTemplateService.saveUnpublished(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processTemplate','process:template:edit')")
    @Operation(summary = "修改")
    @PutMapping("update")
    public Result updateById(@Valid @RequestBody ProcessTemplateUpdateRequest request) {
        processTemplateService.saveUnpublished(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processTemplate','process:template:remove')")
    @Operation(summary = "删除")
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        processTemplateService.removeUnpublished(id);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processTemplate','process:template:upload')")
    @Operation(summary = "上传流程定义")
    @PostMapping("/uploadProcessDefinition")
    public Result uploadProcessDefinition(@RequestParam("file") MultipartFile file) {
        try {
            OaProcessTemplateService.UploadedProcessDefinition uploaded =
                    processTemplateService.uploadProcessDefinition(file);
            Map<String, Object> map = new HashMap<>();
            map.put("processDefinitionPath", uploaded.processDefinitionPath());
            map.put("processDefinitionKey", uploaded.processDefinitionKey());
            return Result.ok(map);
        } catch (IllegalArgumentException e) {
            return Result.fail().message(e.getMessage());
        } catch (IOException e) {
            return Result.fail();
        }
    }

    //部署流程定义（发布）
    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processTemplate','process:template:publish')")
    @Operation(summary = "发布")
    @PostMapping("/publish/{id}")
    public Result publish(@PathVariable Long id) {
        //修改模板发布状态 1 已经发布
        //流程定义部署
        processTemplateService.publish(id);
        return Result.ok();
    }

}

