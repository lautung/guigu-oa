package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDictType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增字典类型的请求体（#251，ADR 0034）：save 只创建、不带主键；状态缺省正常。
 * 公共字段与长度上限在 {@link SysDictTypeRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增字典类型")
public class SysDictTypeSaveRequest extends SysDictTypeRequest {

    @Override
    @NotBlank(message = "字典名称不能为空")
    public String getDictName() {
        return super.getDictName();
    }

    @Override
    @NotBlank(message = "字典类型不能为空")
    public String getDictType() {
        return super.getDictType();
    }

    public SysDictType toEntity() {
        SysDictType type = new SysDictType();
        fill(type);
        return type;
    }
}
