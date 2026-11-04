package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysPost;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增岗位的请求体（#248，ADR 0034）。创建不给主键；长度上限对齐 sys_post 列宽；
 * 状态缺省为 1（正常），与岗位默认可用一致。岗位只是职务标签，不携带授权/数据范围。
 */
@Data
@Schema(description = "新增岗位")
public class SysPostSaveRequest {

    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 50, message = "岗位编码过长")
    @Schema(description = "岗位编码")
    private String postCode;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 50, message = "岗位名称过长")
    @Schema(description = "岗位名称")
    private String name;

    @Schema(description = "排序")
    private Integer sortValue;

    @Schema(description = "状态（1：正常 0：停用），缺省为 1")
    private Integer status;

    public SysPost toEntity() {
        SysPost post = new SysPost();
        post.setPostCode(postCode);
        post.setName(name);
        post.setSortValue(sortValue);
        post.setStatus(status == null ? 1 : status);
        return post;
    }
}
