package com.atguigu.process.attachment;

import com.atguigu.model.process.Attachment;

import java.util.Date;

public record AttachmentView(
        Long id,
        String fileName,
        String contentType,
        Long sizeBytes,
        Date createTime) {

    public static AttachmentView from(Attachment attachment) {
        return new AttachmentView(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreateTime());
    }
}
