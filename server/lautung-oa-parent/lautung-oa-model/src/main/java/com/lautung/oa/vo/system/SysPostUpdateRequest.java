package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysPost;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新岗位的请求体：只要求主键，其余字段为空即不覆盖（ADR 0034）；
 * 长度上限对齐 sys_post 列宽。
 */
@Data
@Schema(description = "更新岗位")
public class SysPostUpdateRequest {

    @NotNull(message = "岗位标识不能为空")
    @Schema(description = "岗位id")
    private Long id;

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

    public SysPost toEntity() {
        SysPost post = new SysPost();
        post.setId(id);
        post.setPostCode(postCode);
        post.setName(name);
        post.setSortValue(sortValue);
        post.setStatus(status);
        return post;
    }
}
