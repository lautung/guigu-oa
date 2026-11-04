package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysPost;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增岗位的请求体（#248，ADR 0034）。创建不给主键；状态缺省为 1（正常），与岗位默认可用一致。
 * 岗位只是职务标签，不携带授权/数据范围。公共字段与长度上限在 {@link SysPostRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增岗位")
public class SysPostSaveRequest extends SysPostRequest {

    @Override
    @NotBlank(message = "岗位编码不能为空")
    public String getPostCode() {
        return super.getPostCode();
    }

    @Override
    @NotBlank(message = "岗位名称不能为空")
    public String getName() {
        return super.getName();
    }

    public SysPost toEntity() {
        SysPost post = new SysPost();
        fill(post);
        if (post.getStatus() == null) {
            post.setStatus(1);
        }
        return post;
    }
}
