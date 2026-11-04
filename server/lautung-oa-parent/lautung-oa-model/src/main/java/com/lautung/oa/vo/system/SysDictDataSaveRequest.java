package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDictData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增字典数据的请求体（#251，ADR 0034）：只做静态形状，类型/值唯一性在 service。
 */
@Data
@Schema(description = "新增字典数据")
public class SysDictDataSaveRequest {

    @NotBlank(message = "字典类型不能为空")
    @Size(max = 100, message = "字典类型过长")
    @Schema(description = "字典类型编码")
    private String dictType;

    @NotBlank(message = "字典标签不能为空")
    @Size(max = 100, message = "字典标签过长")
    @Schema(description = "字典标签")
    private String dictLabel;

    @NotBlank(message = "字典值不能为空")
    @Size(max = 100, message = "字典值过长")
    @Schema(description = "字典值")
    private String dictValue;

    @Schema(description = "排序（升序），缺省 0")
    private Integer sortValue;

    @Schema(description = "状态（1：正常 0：停用），缺省正常")
    private Integer status;

    @Size(max = 500, message = "备注过长")
    @Schema(description = "备注")
    private String remark;

    public SysDictData toEntity() {
        SysDictData data = new SysDictData();
        data.setDictType(dictType);
        data.setDictLabel(dictLabel);
        data.setDictValue(dictValue);
        data.setSortValue(sortValue);
        data.setStatus(status);
        data.setRemark(remark);
        return data;
    }
}
