package com.lautung.oa.model.system;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型（#251，ADR 0062）：运行时可编辑的枚举分类。
 *
 * <p>{@code dict_name} 与 {@code dict_type} 各自唯一；类型删除时若仍有数据行则拒绝，
 * 但不级联删数据（字典不做外键校验）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典类型")
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    private static final long serialVersionUID = 1L;

    @Schema(description = "字典名称")
    @TableField("dict_name")
    private String dictName;

    @Schema(description = "字典类型编码，全局唯一")
    @TableField("dict_type")
    private String dictType;

    @Schema(description = "状态（1：正常 0：停用）")
    @TableField("status")
    private Integer status;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;
}
