package com.atguigu.vo.process;

import com.atguigu.model.process.ProcessTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增审批模板的请求体。流程 zip 不在必填内：草稿可以先没有，发布时才校验（ADR 0034）。
 */
@Data
@Schema(description = "新增审批模板")
public class ProcessTemplateSaveRequest {

    @NotBlank(message = "审批模板名称不能为空")
    @Schema(description = "模板名称")
    private String name;

    @NotNull(message = "审批类型不能为空")
    @Schema(description = "审批类型id")
    private Long processTypeId;

    @Schema(description = "图标路径")
    private String iconUrl;

    @Schema(description = "表单属性")
    private String formProps;

    @Schema(description = "表单选项")
    private String formOptions;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "流程定义key")
    private String processDefinitionKey;

    @Schema(description = "流程定义上传路径")
    private String processDefinitionPath;

    public ProcessTemplate toEntity() {
        ProcessTemplate template = new ProcessTemplate();
        template.setName(name);
        template.setProcessTypeId(processTypeId);
        template.setIconUrl(iconUrl);
        template.setFormProps(formProps);
        template.setFormOptions(formOptions);
        template.setDescription(description);
        template.setProcessDefinitionKey(processDefinitionKey);
        template.setProcessDefinitionPath(processDefinitionPath);
        return template;
    }
}
