package com.atguigu.vo.process;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ApprovalVo {

    private Long processId;

    private String taskId;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "审批描述")
    private String description;
}
