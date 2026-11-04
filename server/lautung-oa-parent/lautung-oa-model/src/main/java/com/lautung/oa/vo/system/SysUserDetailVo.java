package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 用户编辑回读（#249）：用户实体列 + {@code sys_user_post} 的岗位集合。
 * 岗位关联不是用户实体列，单独用 VO 返回，避免把 {@code postIds} 带进写请求的实体落库路径。
 */
@Data
@Schema(description = "用户详情")
public class SysUserDetailVo {

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

    @Schema(description = "岗位id列表")
    private List<Long> postIds;

    @Schema(description = "创建时间")
    private Date createTime;
}
