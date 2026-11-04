package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典数据列表的过滤条件（#251）：必带 {@code dictType}（页面左选类型、右看数据）。
 *
 * <p>没有类型时返回空页，不返回全表——避免「未选类型」把四组种子都倒出来。
 */
@Data
@Schema(description = "字典数据查询条件")
public class SysDictDataQueryVo {

    @Schema(description = "字典类型编码")
    private String dictType;

    @Schema(description = "字典标签（包含匹配）")
    private String dictLabel;

    @Schema(description = "状态（1：正常 0：停用）")
    private Integer status;
}
