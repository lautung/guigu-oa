package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增参数的请求体（#252，ADR 0034）：save 只创建、不带主键，也不接受 builtin——
 * 内置项只由迁移种入。值类型缺省 string。公共字段与长度上限在 {@link SysConfigRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增参数")
public class SysConfigSaveRequest extends SysConfigRequest {

    @Override
    @NotBlank(message = "参数键不能为空")
    public String getConfigKey() {
        return super.getConfigKey();
    }

    @Override
    @NotBlank(message = "参数名称不能为空")
    public String getConfigName() {
        return super.getConfigName();
    }

    public SysConfig toEntity() {
        SysConfig config = new SysConfig();
        fill(config);
        config.setBuiltin(SysConfig.BUILTIN_NO);
        return config;
    }
}
