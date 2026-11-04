package com.lautung.oa.model.system;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据（#251，ADR 0062）：一个类型下的值与标签；同一类型内 {@code dict_value} 唯一。
 *
 * <p>停用行不参与渲染。字典值只是展示映射，不是授权来源，也不参与数据范围。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典数据")
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    private static final long serialVersionUID = 1L;

    @Schema(description = "字典类型编码")
    @TableField("dict_type")
    private String dictType;

    @Schema(description = "字典标签")
    @TableField("dict_label")
    private String dictLabel;

    @Schema(description = "字典值")
    @TableField("dict_value")
    private String dictValue;

    @Schema(description = "排序（升序）")
    @TableField("sort_value")
    private Integer sortValue;

    @Schema(description = "状态（1：正常 0：停用）")
    @TableField("status")
    private Integer status;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;
}
