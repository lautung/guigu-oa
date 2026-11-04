package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新参数的请求体（#252，ADR 0034）：只要求主键，其余字段为空即不覆盖。
 * builtin 不来自请求；内置项的保护由 service 按库里的行判定。
 */
@Data
@Schema(description = "更新参数")
public class SysConfigUpdateRequest {

    @NotNull(message = "参数标识不能为空")
    @Schema(description = "参数id")
    private Long id;

    @Size(max = 100, message = "参数键过长")
    @Schema(description = "参数键")
    private String configKey;

    @Size(max = 100, message = "参数名称过长")
    @Schema(description = "参数名称")
    private String configName;

    @Size(max = 500, message = "参数值过长")
    @Schema(description = "参数值")
    private String configValue;

    @Size(max = 20, message = "参数类型过长")
    @Schema(description = "值类型（string / number / boolean）")
    private String valueType;

    @Size(max = 500, message = "备注过长")
    @Schema(description = "备注")
    private String remark;

    public SysConfig toEntity() {
        SysConfig config = new SysConfig();
        config.setId(id);
        config.setConfigKey(configKey);
        config.setConfigName(configName);
        config.setConfigValue(configValue);
        config.setValueType(valueType);
        config.setRemark(remark);
        return config;
    }
}
