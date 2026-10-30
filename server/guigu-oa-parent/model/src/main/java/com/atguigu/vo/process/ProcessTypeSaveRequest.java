package com.atguigu.vo.process;

import com.atguigu.model.process.ProcessType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增审批类型的请求体（ADR 0034）。长度上限对齐 oa_process_type 列宽（ADR 0036）。
 */
@Data
@Schema(description = "新增审批类型")
public class ProcessTypeSaveRequest {

    @NotBlank(message = "审批类型名称不能为空")
    @Size(max = 50, message = "审批类型名称过长")
    @Schema(description = "类型名称")
    private String name;

    @Size(max = 255, message = "审批类型描述过长")
    @Schema(description = "描述")
    private String description;

    public ProcessType toEntity() {
        ProcessType processType = new ProcessType();
        processType.setName(name);
        processType.setDescription(description);
        return processType;
    }
}
