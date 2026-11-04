package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新参数的请求体（#252，ADR 0034）：只要求主键，其余字段为空即不覆盖。
 * builtin 不来自请求；内置项的保护由 service 按库里的行判定。公共字段与长度上限在 {@link SysConfigRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新参数")
public class SysConfigUpdateRequest extends SysConfigRequest {

    @NotNull(message = "参数标识不能为空")
    @Schema(description = "参数id")
    private Long id;

    public SysConfig toEntity() {
        SysConfig config = new SysConfig();
        config.setId(id);
        fill(config);
        return config;
    }
}
