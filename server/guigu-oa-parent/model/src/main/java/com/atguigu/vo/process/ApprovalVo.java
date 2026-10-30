package com.atguigu.vo.process;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalVo {

    @NotNull(message = "审批标识不能为空")
    private Long processId;

    @NotBlank(message = "待办任务标识不能为空")
    private String taskId;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "审批描述")
    private String description;
}
