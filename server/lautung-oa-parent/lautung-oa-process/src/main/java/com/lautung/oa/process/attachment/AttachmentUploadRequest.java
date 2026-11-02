package com.lautung.oa.process.attachment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AttachmentUploadRequest {

    private Long processId;
    private String draftId;

    @NotBlank(message = "附件文件名不能为空")
    @Size(max = 512, message = "附件文件名过长")
    private String fileName;

    private String contentType;

    @NotNull(message = "附件大小不能为空")
    @Positive(message = "附件大小必须大于 0")
    private Long sizeBytes;
}
