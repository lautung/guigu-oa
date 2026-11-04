package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人中心改密（#254）：改密不是重置密码，必须验旧密码（ADR 0029/0062）。
 * 新密码长度策略沿用 min 6，上限对齐 {@code sys_user.password} 列宽。
 */
@Data
@Schema(description = "个人中心修改密码")
public class ProfilePasswordRequest {

    @NotBlank(message = "旧密码不能为空")
    @Schema(description = "旧密码")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在 6 到 100 位之间")
    @Schema(description = "新密码（明文，服务端编码后落库）")
    private String newPassword;
}
