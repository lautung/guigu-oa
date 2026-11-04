package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 锁屏解锁：只提交当前用户的密码（#255）。校验不签发令牌、不改会话。
 */
@Schema(description = "校验当前用户密码")
public class VerifyPasswordVo {

    @NotBlank(message = "密码不能为空")
    @Schema(description = "当前用户密码")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
