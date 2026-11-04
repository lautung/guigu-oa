package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDictType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新字典类型的请求体（#251，ADR 0034）：只要求主键，其余字段为空即不覆盖。
 * 公共字段与长度上限在 {@link SysDictTypeRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新字典类型")
public class SysDictTypeUpdateRequest extends SysDictTypeRequest {

    @NotNull(message = "字典类型标识不能为空")
    @Schema(description = "字典类型id")
    private Long id;

    public SysDictType toEntity() {
        SysDictType type = new SysDictType();
        type.setId(id);
        fill(type);
        return type;
    }
}
