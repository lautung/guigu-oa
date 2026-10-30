package com.atguigu.vo.process;

import com.atguigu.model.process.ProcessTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新审批模板的请求体。只要求主键：其余字段为空即不覆盖（ADR 0034）。
 */
@Data
@Schema(description = "更新审批模板")
public class ProcessTemplateUpdateRequest {

    @NotNull(message = "审批模板标识不能为空")
    @Schema(description = "审批模板id")
    private Long id;

    @Schema(description = "模板名称")
    private String name;

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
        template.setId(id);
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
