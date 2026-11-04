package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SysConfig 写请求的公共核心（ADR 0047）：Save/Update 两半注解一致的字段与落库填充。
 * configKey/configName 的必填性是 ADR 0034 seam 的两半语义（save 必填 / update 空=不覆盖），
 * 由各壳在自己的 getter 上声明；长度上限只在这里写一次。
 * builtin 不来自请求；内置项的保护由 service 按库里的行判定。
 */
@Data
public abstract class SysConfigRequest {

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

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(SysConfig config) {
        config.setConfigKey(configKey);
        config.setConfigName(configName);
        config.setConfigValue(configValue);
        config.setValueType(valueType);
        config.setRemark(remark);
    }
}
