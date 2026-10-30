package com.atguigu.vo.system;

import com.atguigu.model.system.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户的请求体。只要求主键：其余字段为空即不覆盖，密码为空表示不改密（ADR 0034）；
 * 长度上限对齐 sys_user 列宽（ADR 0036）。
 */
@Data
@Schema(description = "更新用户")
public class SysUserUpdateRequest {

    @NotNull(message = "用户标识不能为空")
    @Schema(description = "用户id")
    private Long id;

    @Size(max = 20, message = "用户名过长")
    @Schema(description = "用户名")
    private String username;

    @Schema(description = "新密码（明文；为空表示不改密）")
    private String password;

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
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setName(name);
        user.setPhone(phone);
        user.setStatus(status);
        return user;
    }
}
