package com.lautung.oa.vo.process;

import com.lautung.oa.model.process.ProcessTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新审批模板的请求体。只要求主键：其余字段为空即不覆盖（ADR 0034）；长度上限对齐 oa_process_template 列宽（ADR 0036）。
 * 公共字段与 fill 在 {@link ProcessTemplateRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新审批模板")
public class ProcessTemplateUpdateRequest extends ProcessTemplateRequest {

    @NotNull(message = "审批模板标识不能为空")
    @Schema(description = "审批模板id")
    private Long id;

    @Size(max = 50, message = "审批模板名称过长")
    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "审批类型id")
    private Long processTypeId;

    public ProcessTemplate toEntity() {
        ProcessTemplate template = new ProcessTemplate();
        template.setId(id);
        template.setName(name);
        template.setProcessTypeId(processTypeId);
        fill(template);
        return template;
    }
}
