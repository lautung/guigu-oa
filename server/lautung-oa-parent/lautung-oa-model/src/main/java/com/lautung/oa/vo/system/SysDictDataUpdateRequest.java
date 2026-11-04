package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDictData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新字典数据的请求体（#251，ADR 0034）：只要求主键，其余字段为空即不覆盖。
 * 公共字段与长度上限在 {@link SysDictDataRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新字典数据")
public class SysDictDataUpdateRequest extends SysDictDataRequest {

    @NotNull(message = "字典数据标识不能为空")
    @Schema(description = "字典数据id")
    private Long id;

    public SysDictData toEntity() {
        SysDictData data = new SysDictData();
        data.setId(id);
        fill(data);
        return data;
    }
}
