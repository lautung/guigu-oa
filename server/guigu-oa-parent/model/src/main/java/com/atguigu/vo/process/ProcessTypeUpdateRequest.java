package com.atguigu.vo.process;

import com.atguigu.model.process.ProcessType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新审批类型的请求体。只要求主键：其余字段为空即不覆盖（ADR 0034）；长度上限对齐 oa_process_type 列宽（ADR 0036）。
 */
@Data
@Schema(description = "更新审批类型")
public class ProcessTypeUpdateRequest {

    @NotNull(message = "审批类型标识不能为空")
    @Schema(description = "审批类型id")
    private Long id;

    @Size(max = 50, message = "审批类型名称过长")
    @Schema(description = "类型名称")
    private String name;

    @Size(max = 255, message = "审批类型描述过长")
    @Schema(description = "描述")
    private String description;

    public ProcessType toEntity() {
        ProcessType processType = new ProcessType();
        processType.setId(id);
        processType.setName(name);
        processType.setDescription(description);
        return processType;
    }
}
