package com.atguigu.model.process;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.io.Serializable;

/** Backend-only proof tying a frozen template to its immutable archive and exact engine definition. */
@Data
@TableName("oa_process_template_publication")
public class ProcessTemplatePublication implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("process_template_id")
    private Long processTemplateId;

    @TableField("artifact_path")
    private String artifactPath;

    @TableField("artifact_sha256")
    private String artifactSha256;

    @TableField("process_definition_key")
    private String processDefinitionKey;

    @TableField("deployment_id")
    private String deploymentId;

    @TableField("process_definition_id")
    private String processDefinitionId;
}
