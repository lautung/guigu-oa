package com.lautung.oa.process.attachment;

import java.util.List;

public record ProcessAttachmentList(List<AttachmentView> attachments, boolean canUpload) {
}
