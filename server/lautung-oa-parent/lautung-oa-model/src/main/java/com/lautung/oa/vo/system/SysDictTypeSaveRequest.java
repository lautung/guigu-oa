package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDictType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增字典类型的请求体（#251，ADR 0034）：save 只创建、不带主键；状态缺省正常。
 */
@Data
@Schema(description = "新增字典类型")
public class SysDictTypeSaveRequest {

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100, message = "字典名称过长")
    @Schema(description = "字典名称")
    private String dictName;

    @NotBlank(message = "字典类型不能为空")
    @Size(max = 100, message = "字典类型过长")
    @Schema(description = "字典类型编码")
    private String dictType;

    @Schema(description = "状态（1：正常 0：停用），缺省正常")
    private Integer status;

    @Size(max = 500, message = "备注过长")
    @Schema(description = "备注")
    private String remark;

    public SysDictType toEntity() {
        SysDictType type = new SysDictType();
        type.setDictName(dictName);
        type.setDictType(dictType);
        type.setStatus(status);
        type.setRemark(remark);
        return type;
    }
}
