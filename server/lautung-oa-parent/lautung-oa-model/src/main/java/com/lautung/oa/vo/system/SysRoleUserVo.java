package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色侧成员列表的一行（#256）。只暴露双列表需要的字段，不含密码哈希与令牌版本。
 */
@Data
@Schema(description = "角色成员")
public class SysRoleUserVo {

    @Schema(description = "用户id")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "手机")
    private String phone;

    @Schema(description = "部门id")
    private Long deptId;

    @Schema(description = "状态（1：正常 0：停用）")
    private Integer status;

    @Schema(description = "是否已属于该角色")
    private Boolean assigned;
}
