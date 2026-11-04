package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 参数列表的过滤条件（#252）：键与名称按包含匹配，读不设权限点（ADR 0056）。
 */
@Data
@Schema(description = "参数查询条件")
public class SysConfigQueryVo {

    @Schema(description = "参数键（包含匹配）")
    private String configKey;

    @Schema(description = "参数名称（包含匹配）")
    private String configName;
}
