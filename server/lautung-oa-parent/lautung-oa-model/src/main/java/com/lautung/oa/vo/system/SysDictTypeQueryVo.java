package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典类型列表的过滤条件（#251）：名称与编码按包含匹配，状态精确匹配。
 */
@Data
@Schema(description = "字典类型查询条件")
public class SysDictTypeQueryVo {

    @Schema(description = "字典名称（包含匹配）")
    private String dictName;

    @Schema(description = "字典类型编码（包含匹配）")
    private String dictType;

    @Schema(description = "状态（1：正常 0：停用）")
    private Integer status;
}
