package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 业务单据提交（#282，ADR 0067/0032）：客户端生成的幂等键必填，
 * 同键重放返回同一条审批；不同键的第二次提交由单据状态 CAS 拒绝。
 */
@Data
@Schema(description = "单据提交")
public class AttendanceApplySubmitRequest {

    @NotBlank(message = "提交标识不能为空")
    @Size(max = 64, message = "提交标识过长")
    @Schema(description = "客户端生成的提交标识（必填）")
    private String idempotencyKey;
}
