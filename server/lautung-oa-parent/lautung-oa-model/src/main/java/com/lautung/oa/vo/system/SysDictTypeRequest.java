package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDictType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SysDictType 写请求的公共核心（ADR 0047）：Save/Update 两半注解一致的字段与落库填充。
 * dictName/dictType 的必填性是 ADR 0034 seam 的两半语义（save 必填 / update 空=不覆盖），
 * 由各壳在自己的 getter 上声明；长度上限只在这里写一次。
 */
@Data
public abstract class SysDictTypeRequest {

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

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(SysDictType type) {
        type.setDictName(dictName);
        type.setDictType(dictType);
        type.setStatus(status);
        type.setRemark(remark);
    }
}
