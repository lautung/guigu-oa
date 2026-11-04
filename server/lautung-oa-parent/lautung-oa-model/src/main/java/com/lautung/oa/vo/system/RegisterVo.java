package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 匿名注册的请求体（#253，ADR 0062）：username/password/confirm/name 必填，手机可选。
 *
 * <p>文案与客户端表单逐字一致；confirm 是「确认密码」，由 controller 在 service 之前比对，
 * 不落库、不进审计。字段上限对齐 {@code sys_user} 列宽（username/name/phone 20）。
 */
@Data
@Schema(description = "注册")
public class RegisterVo implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 20, message = "用户名过长")
    @Schema(description = "用户名")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在 6 到 100 位之间")
    @Schema(description = "密码")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "确认密码")
    private String confirm;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 20, message = "姓名过长")
    @Schema(description = "姓名")
    private String name;

    @Size(max = 20, message = "手机号过长")
    @Schema(description = "手机（可选）")
    private String phone;
}
