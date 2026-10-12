package com.atguigu.process.attachment;

import lombok.Data;

@Data
public class AttachmentUploadRequest {

    private Long processId;
    private String draftId;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
}
