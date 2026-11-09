package com.lautung.oa.vo.process;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferVo {

    @NotNull(message = "转办目标不能为空")
    @Schema(description = "转办目标用户id（不能是本人或申请人）")
    private Long targetUserId;
}
