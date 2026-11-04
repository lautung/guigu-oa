package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDictType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新字典类型的请求体（#251，ADR 0034）：只要求主键，其余字段为空即不覆盖。
 */
@Data
@Schema(description = "更新字典类型")
public class SysDictTypeUpdateRequest {

    @NotNull(message = "字典类型标识不能为空")
    @Schema(description = "字典类型id")
    private Long id;

    @Size(max = 100, message = "字典名称过长")
    @Schema(description = "字典名称")
    private String dictName;

    @Size(max = 100, message = "字典类型过长")
    @Schema(description = "字典类型编码")
    private String dictType;

    @Schema(description = "状态（1：正常 0：停用）")
    private Integer status;

    @Size(max = 500, message = "备注过长")
    @Schema(description = "备注")
    private String remark;

    public SysDictType toEntity() {
        SysDictType type = new SysDictType();
        type.setId(id);
        type.setDictName(dictName);
        type.setDictType(dictType);
        type.setStatus(status);
        type.setRemark(remark);
        return type;
    }
}
