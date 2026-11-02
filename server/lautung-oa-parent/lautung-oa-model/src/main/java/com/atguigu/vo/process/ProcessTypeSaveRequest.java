package com.atguigu.vo.process;

import com.atguigu.model.process.ProcessType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增审批类型的请求体（ADR 0034）。长度上限对齐 oa_process_type 列宽（ADR 0036）。
 * 公共字段与 fill 在 {@link ProcessTypeRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增审批类型")
public class ProcessTypeSaveRequest extends ProcessTypeRequest {

    @NotBlank(message = "审批类型名称不能为空")
    @Size(max = 50, message = "审批类型名称过长")
    @Schema(description = "类型名称")
    private String name;

    public ProcessType toEntity() {
        ProcessType processType = new ProcessType();
        processType.setName(name);
        fill(processType);
        return processType;
    }
}
