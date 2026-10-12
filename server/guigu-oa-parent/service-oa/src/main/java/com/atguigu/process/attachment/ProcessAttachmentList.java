package com.atguigu.process.attachment;

import java.util.List;

public record ProcessAttachmentList(List<AttachmentView> attachments, boolean canUpload) {
}
