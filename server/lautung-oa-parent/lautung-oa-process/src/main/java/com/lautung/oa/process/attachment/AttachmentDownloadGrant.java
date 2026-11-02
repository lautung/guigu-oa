package com.lautung.oa.process.attachment;

import java.util.Date;

public record AttachmentDownloadGrant(String downloadUrl, Date expiresAt) {
}
