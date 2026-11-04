package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysPost;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新岗位的请求体：只要求主键，其余字段为空即不覆盖（ADR 0034）。
 * 公共字段与长度上限在 {@link SysPostRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新岗位")
public class SysPostUpdateRequest extends SysPostRequest {

    @NotNull(message = "岗位标识不能为空")
    @Schema(description = "岗位id")
    private Long id;

    public SysPost toEntity() {
        SysPost post = new SysPost();
        post.setId(id);
        fill(post);
        return post;
    }
}
