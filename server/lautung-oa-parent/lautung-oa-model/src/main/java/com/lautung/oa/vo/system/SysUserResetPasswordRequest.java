package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码的请求体（#250）：管理员为他人设置新密码，不需要旧密码，也不改用户的其它资料。
 * 只收新密码；必填与长度策略沿用改密/注册口径（min 6），上限对齐 {@code sys_user.password}
 * 列宽，越界给字段文案而不是落库兜底（ADR 0036）。
 */
@Data
@Schema(description = "重置密码")
public class SysUserResetPasswordRequest {

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在 6 到 100 位之间")
    @Schema(description = "新密码（明文，服务端编码后落库）")
    private String newPassword;
}
