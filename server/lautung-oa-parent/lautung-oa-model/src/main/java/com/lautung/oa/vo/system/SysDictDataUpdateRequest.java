package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDictData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新字典数据的请求体（#251，ADR 0034）：只要求主键，其余字段为空即不覆盖。
 */
@Data
@Schema(description = "更新字典数据")
public class SysDictDataUpdateRequest {

    @NotNull(message = "字典数据标识不能为空")
    @Schema(description = "字典数据id")
    private Long id;

    @Size(max = 100, message = "字典类型过长")
    @Schema(description = "字典类型编码")
    private String dictType;

    @Size(max = 100, message = "字典标签过长")
    @Schema(description = "字典标签")
    private String dictLabel;

    @Size(max = 100, message = "字典值过长")
    @Schema(description = "字典值")
    private String dictValue;

    @Schema(description = "排序（升序）")
    private Integer sortValue;

    @Schema(description = "状态（1：正常 0：停用）")
    private Integer status;

    @Size(max = 500, message = "备注过长")
    @Schema(description = "备注")
    private String remark;

    public SysDictData toEntity() {
        SysDictData data = new SysDictData();
        data.setId(id);
        data.setDictType(dictType);
        data.setDictLabel(dictLabel);
        data.setDictValue(dictValue);
        data.setSortValue(sortValue);
        data.setStatus(status);
        data.setRemark(remark);
        return data;
    }
}
