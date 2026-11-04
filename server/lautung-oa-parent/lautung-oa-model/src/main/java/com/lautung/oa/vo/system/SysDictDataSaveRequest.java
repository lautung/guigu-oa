package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDictData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增字典数据的请求体（#251，ADR 0034）：只做静态形状，类型/值唯一性在 service。
 * 公共字段与长度上限在 {@link SysDictDataRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增字典数据")
public class SysDictDataSaveRequest extends SysDictDataRequest {

    @Override
    @NotBlank(message = "字典类型不能为空")
    public String getDictType() {
        return super.getDictType();
    }

    @Override
    @NotBlank(message = "字典标签不能为空")
    public String getDictLabel() {
        return super.getDictLabel();
    }

    @Override
    @NotBlank(message = "字典值不能为空")
    public String getDictValue() {
        return super.getDictValue();
    }

    public SysDictData toEntity() {
        SysDictData data = new SysDictData();
        fill(data);
        return data;
    }
}
