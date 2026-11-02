package com.lautung.oa.vo.process;

import com.lautung.oa.model.process.ProcessType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ProcessType 写请求的公共核心：持有 Save/Update 两半注解完全一致的字段与落库填充（ADR 0047）。
 * name 的必填性是 ADR 0034 seam 的两半语义（save 必填 / update 空=不覆盖），留在各壳。
 */
@Data
public abstract class ProcessTypeRequest {

    @Size(max = 255, message = "审批类型描述过长")
    @Schema(description = "描述")
    private String description;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(ProcessType processType) {
        processType.setDescription(description);
    }
}
