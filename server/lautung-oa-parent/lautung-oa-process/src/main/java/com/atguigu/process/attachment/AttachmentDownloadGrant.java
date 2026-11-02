package com.atguigu.process.attachment;

import java.util.Date;

public record AttachmentDownloadGrant(String downloadUrl, Date expiresAt) {
}
