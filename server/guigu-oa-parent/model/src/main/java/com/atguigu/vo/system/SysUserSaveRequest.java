package com.atguigu.vo.system;

import com.atguigu.model.system.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增用户的请求体。创建既不给主键，也不接受空密码：空白密码只会建出一个不能登录的账号（ADR 0034）；
 * 长度上限对齐 sys_user 列宽，越界给字段文案而不是落库兜底（ADR 0036）。
 */
@Data
@Schema(description = "新增用户")
public class SysUserSaveRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 20, message = "用户名过长")
    @Schema(description = "用户名")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码（明文，服务端编码后落库）")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 20, message = "姓名过长")
    @Schema(description = "姓名")
    private String name;

    @Size(max = 20, message = "手机号过长")
    @Schema(description = "手机")
    private String phone;

    @Schema(description = "状态（1：正常 0：停用）")
    private Integer status;

    public SysUser toEntity() {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(password);
        user.setName(name);
        user.setPhone(phone);
        user.setStatus(status);
        return user;
    }
}
