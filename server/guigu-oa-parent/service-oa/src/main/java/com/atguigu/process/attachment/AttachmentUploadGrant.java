package com.atguigu.process.attachment;

import java.util.Date;
import java.util.Map;

public record AttachmentUploadGrant(
        Long attachmentId,
        String uploadUrl,
        Map<String, String> headers,
        Date expiresAt) {
}
