package com.lautung.oa.vo.process;

import com.lautung.oa.model.process.ProcessTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ProcessTemplate 写请求的公共核心：持有 Save/Update 两半注解完全一致的字段与落库填充（ADR 0047）。
 * 必填性注解（name/processTypeId）是 ADR 0034 seam 的两半语义（save 必填 / update 空=不覆盖），留在各壳；
 * formProps/formOptions 是 TEXT 列不设上限（ADR 0036）。
 */
@Data
public abstract class ProcessTemplateRequest {

    @Size(max = 200, message = "图标路径过长")
    @Schema(description = "图标路径")
    private String iconUrl;

    @Schema(description = "表单属性")
    private String formProps;

    @Schema(description = "表单选项")
    private String formOptions;

    @Size(max = 255, message = "模板描述过长")
    @Schema(description = "描述")
    private String description;

    @Size(max = 100, message = "流程定义key过长")
    @Schema(description = "流程定义key")
    private String processDefinitionKey;

    @Size(max = 200, message = "流程定义上传路径过长")
    @Schema(description = "流程定义上传路径")
    private String processDefinitionPath;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(ProcessTemplate template) {
        template.setIconUrl(iconUrl);
        template.setFormProps(formProps);
        template.setFormOptions(formOptions);
        template.setDescription(description);
        template.setProcessDefinitionKey(processDefinitionKey);
        template.setProcessDefinitionPath(processDefinitionPath);
    }
}
