package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人中心资料更新（#254）：只收姓名/手机，没有用户名、密码、状态等字段——
 * 用户名只读，改密走独立端点并必须验旧密码。长度上限对齐 {@code sys_user} 列宽（ADR 0036）。
 */
@Data
@Schema(description = "个人中心资料更新")
public class ProfileUpdateRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 20, message = "姓名过长")
    @Schema(description = "姓名")
    private String name;

    @Size(max = 20, message = "手机号过长")
    @Schema(description = "手机")
    private String phone;
}
