package com.lautung.oa.model.system;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户与岗位的关联行（#248）：仅在用户写路径按提交集合整体替换，岗位侧不反向写用户。
 */
@Data
@Schema(description = "用户岗位")
@TableName("sys_user_post")
public class SysUserPost extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户id")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "岗位id")
    @TableField("post_id")
    private Long postId;
}
