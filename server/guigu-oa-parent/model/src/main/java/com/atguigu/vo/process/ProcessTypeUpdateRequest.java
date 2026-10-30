package com.atguigu.vo.process;

import com.atguigu.model.process.ProcessType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新审批类型的请求体。只要求主键：其余字段为空即不覆盖（ADR 0034）。
 */
@Data
@Schema(description = "更新审批类型")
public class ProcessTypeUpdateRequest {

    @NotNull(message = "审批类型标识不能为空")
    @Schema(description = "审批类型id")
    private Long id;

    @Schema(description = "类型名称")
    private String name;

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
