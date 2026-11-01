package com.atguigu.vo.process;

import com.atguigu.model.process.ProcessTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增审批模板的请求体。流程 zip 不在必填内：草稿可以先没有，发布时才校验（ADR 0034）；
 * 长度上限对齐 oa_process_template 列宽（ADR 0036）。公共字段与 fill 在 {@link ProcessTemplateRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增审批模板")
public class ProcessTemplateSaveRequest extends ProcessTemplateRequest {

    @NotBlank(message = "审批模板名称不能为空")
    @Size(max = 50, message = "审批模板名称过长")
    @Schema(description = "模板名称")
    private String name;

    @NotNull(message = "审批类型不能为空")
    @Schema(description = "审批类型id")
    private Long processTypeId;

    public ProcessTemplate toEntity() {
        ProcessTemplate template = new ProcessTemplate();
        template.setName(name);
        template.setProcessTypeId(processTypeId);
        fill(template);
        return template;
    }
}
