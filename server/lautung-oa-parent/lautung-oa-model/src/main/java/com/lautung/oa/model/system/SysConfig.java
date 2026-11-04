package com.lautung.oa.model.system;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 参数（#252，ADR 0062）：运行时可改的键值配置，不是字典。
 *
 * <p>{@code builtin=1} 的行由迁移种入，不可删除、不可改 key，只允许改 value；
 * 保护判定在 service，DTO 只做静态形状（ADR 0034）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "参数")
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    public static final int BUILTIN_NO = 0;
    public static final int BUILTIN_YES = 1;

    public static final String TYPE_STRING = "string";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_BOOLEAN = "boolean";

    private static final long serialVersionUID = 1L;

    @Schema(description = "参数键，全局唯一")
    @TableField("config_key")
    private String configKey;

    @Schema(description = "参数值")
    @TableField("config_value")
    private String configValue;

    @Schema(description = "参数名称")
    @TableField("config_name")
    private String configName;

    @Schema(description = "值类型（string / number / boolean），只做展示与轻校验")
    @TableField("value_type")
    private String valueType;

    @Schema(description = "是否内置（1：是 0：否）")
    @TableField("builtin")
    private Integer builtin;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;
}
