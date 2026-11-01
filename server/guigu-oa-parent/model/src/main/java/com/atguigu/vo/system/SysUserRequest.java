package com.atguigu.vo.system;

import com.atguigu.model.system.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SysUser 写请求的公共核心：持有 Save/Update 两半注解完全一致的字段与落库填充（ADR 0047）。
 * 必填性注解是 ADR 0034 seam 的两半语义（save 必填 / update 空=不覆盖），留在各壳；
 * password 两半语义分歧（必填建号 vs 空即不改密），同样不上收。
 */
@Data
public abstract class SysUserRequest {

    @Size(max = 20, message = "手机号过长")
    @Schema(description = "手机")
    private String phone;

    @Schema(description = "状态（1：正常 0：停用）")
    private Integer status;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(SysUser user) {
        user.setPhone(phone);
        user.setStatus(status);
    }
}
