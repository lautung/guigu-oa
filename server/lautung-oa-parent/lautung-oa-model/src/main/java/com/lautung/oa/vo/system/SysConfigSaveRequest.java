package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增参数的请求体（#252，ADR 0034）：save 只创建、不带主键，也不接受 builtin——
 * 内置项只由迁移种入。长度上限对齐 sys_config 列宽，值类型缺省 string。
 */
@Data
@Schema(description = "新增参数")
public class SysConfigSaveRequest {

    @NotBlank(message = "参数键不能为空")
    @Size(max = 100, message = "参数键过长")
    @Schema(description = "参数键")
    private String configKey;

    @NotBlank(message = "参数名称不能为空")
    @Size(max = 100, message = "参数名称过长")
    @Schema(description = "参数名称")
    private String configName;

    @Size(max = 500, message = "参数值过长")
    @Schema(description = "参数值")
    private String configValue;

    @Size(max = 20, message = "参数类型过长")
    @Schema(description = "值类型（string / number / boolean），缺省 string")
    private String valueType;

    @Size(max = 500, message = "备注过长")
    @Schema(description = "备注")
    private String remark;

    public SysConfig toEntity() {
        SysConfig config = new SysConfig();
        config.setConfigKey(configKey);
        config.setConfigName(configName);
        config.setConfigValue(configValue);
        config.setValueType(valueType);
        config.setRemark(remark);
        config.setBuiltin(SysConfig.BUILTIN_NO);
        return config;
    }
}
