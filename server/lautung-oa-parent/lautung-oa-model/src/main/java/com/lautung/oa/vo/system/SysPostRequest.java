package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysPost;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SysPost 写请求的公共核心（ADR 0047）：Save/Update 两半注解一致的字段与落库填充。
 * postCode/name 的必填性是 ADR 0034 seam 的两半语义（save 必填 / update 空=不覆盖），
 * 由各壳在自己的 getter 上声明；长度上限只在这里写一次。
 */
@Data
public abstract class SysPostRequest {

    @Size(max = 50, message = "岗位编码过长")
    @Schema(description = "岗位编码")
    private String postCode;

    @Size(max = 50, message = "岗位名称过长")
    @Schema(description = "岗位名称")
    private String name;

    @Schema(description = "排序")
    private Integer sortValue;

    @Schema(description = "状态（1：正常 0：停用）")
    private Integer status;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(SysPost post) {
        post.setPostCode(postCode);
        post.setName(name);
        post.setSortValue(sortValue);
        post.setStatus(status);
    }
}
