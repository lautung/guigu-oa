package com.atguigu.process.controller;


import com.atguigu.common.result.Result;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.process.service.OaProcessTemplateService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Operation(summary = "获取分页审批模板数据")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,
                        @PathVariable Long limit) {
        Page<ProcessTemplate> pageParam = new Page<>(page,limit);
        //分页查询审批模板，把审批类型对应名称查询
        IPage<ProcessTemplate> pageModel =
                processTemplateService.findPage(pageParam);
        return Result.ok(pageModel);
    }

    //@PreAuthorize("hasAuthority('bnt.processTemplate.list')")
    @Operation(summary = "获取")
    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id) {
        ProcessTemplate processTemplate = processTemplateService.read(id);
        return Result.ok(processTemplate);
    }

    //@PreAuthorize("hasAuthority('bnt.processTemplate.templateSet')")
    @Operation(summary = "新增")
    @PostMapping("save")
    public Result save(@RequestBody ProcessTemplate processTemplate) {
        processTemplateService.saveUnpublished(processTemplate);
        return Result.ok();
    }

    //@PreAuthorize("hasAuthority('bnt.processTemplate.templateSet')")
    @Operation(summary = "修改")
    @PutMapping("update")
    public Result updateById(@RequestBody ProcessTemplate processTemplate) {
        processTemplateService.saveUnpublished(processTemplate);
        return Result.ok();
    }

    //@PreAuthorize("hasAuthority('bnt.processTemplate.remove')")
    @Operation(summary = "删除")
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        processTemplateService.removeUnpublished(id);
        return Result.ok();
    }

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
    @Operation(summary = "发布")
    @GetMapping("/publish/{id}")
    public Result publish(@PathVariable Long id) {
        //修改模板发布状态 1 已经发布
        //流程定义部署
        processTemplateService.publish(id);
        return Result.ok();
    }

}

