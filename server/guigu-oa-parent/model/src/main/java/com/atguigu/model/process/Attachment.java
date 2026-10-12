package com.atguigu.model.process;

import com.atguigu.model.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("oa_attachment")
public class Attachment extends BaseEntity {

    public static final String UPLOADING = "UPLOADING";
    public static final String PENDING = "PENDING";
    public static final String ATTACHED = "ATTACHED";
    public static final String DELETED = "DELETED";

    @TableField("process_id")
    private Long processId;

    @TableField("owner_user_id")
    private Long ownerUserId;

    @TableField("draft_id")
    private String draftId;

    @TableField("storage_id")
    private String storageId;

    @TableField("object_key")
    private String objectKey;

    @TableField("temporary_object_key")
    private String temporaryObjectKey;

    @TableField("file_name")
    private String fileName;

    @TableField("content_type")
    private String contentType;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("status")
    private String status;

    @TableField("upload_expires_at")
    private Date uploadExpiresAt;
}
