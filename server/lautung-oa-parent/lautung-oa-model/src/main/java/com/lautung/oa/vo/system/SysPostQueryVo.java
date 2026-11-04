package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 岗位列表的过滤条件（#248）：三段都可以缺省，缺省即不过滤。
 */
@Data
@Schema(description = "岗位查询条件")
public class SysPostQueryVo {

    @Schema(description = "岗位编码（模糊）")
    private String postCode;

    @Schema(description = "岗位名称（模糊）")
    private String name;

    @Schema(description = "状态（1：正常 0：停用）")
    private Integer status;
}
