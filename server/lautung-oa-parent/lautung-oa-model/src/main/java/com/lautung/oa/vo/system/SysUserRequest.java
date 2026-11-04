package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SysUser 写请求的公共核心：持有 Save/Update 两半注解完全一致的字段与落库填充（ADR 0047）。
 * 必填性注解是 ADR 0034 seam 的两半语义（save 必填 / update 空=不覆盖），留在各壳；
 * password 两半语义分歧（必填建号 vs 空即不改密），同样不上收。
 * deptId 两半都不带注解（save 可空=不归属 / update 空=不覆盖），存在且启用由 service 判定（#249）。
 * postIds 两半语义分歧（save 缺省=不挂岗位 / update 未传=保留、空列表=清空），由 service 判定。
 */
@Data
public abstract class SysUserRequest {

    @Size(max = 20, message = "手机号过长")
    @Schema(description = "手机")
    private String phone;

    @Schema(description = "状态（1：正常 0：停用）")
    private Integer status;

    @Schema(description = "部门id（save 可空=不归属；update 空=不覆盖）")
    private Long deptId;

    @Schema(description = "岗位id列表（save 缺省=不挂；update 未传=保留、空列表=清空）")
    private List<Long> postIds;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(SysUser user) {
        user.setPhone(phone);
        user.setStatus(status);
        user.setDeptId(deptId);
    }
}
