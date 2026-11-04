package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDictData;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 客户端渲染字典标签的最小形状（#251，ADR 0062）：只有值与标签（外加排序）。
 *
 * <p>管理页的停用/备注字段不下发；读路径按 {@code sort_value} 升序、跳过停用行。
 * 这个 VO 也是 {@code dict:<dictType>} 缓存键的 JSON 形状。
 */
@Schema(description = "字典数据项")
public record DictDataVo(
        @Schema(description = "字典标签") String dictLabel,
        @Schema(description = "字典值") String dictValue,
        @Schema(description = "排序") Integer sortValue) {

    public static DictDataVo of(SysDictData data) {
        return new DictDataVo(data.getDictLabel(), data.getDictValue(), data.getSortValue());
    }
}
