-- 审批附件元数据；文件内容保存在私有 S3 兼容对象存储。
CREATE TABLE oa_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  process_id BIGINT DEFAULT NULL,
  owner_user_id BIGINT NOT NULL,
  draft_id VARCHAR(36) DEFAULT NULL,
  storage_id VARCHAR(36) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  temporary_object_key VARCHAR(512) DEFAULT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(255) NOT NULL,
  size_bytes BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  upload_expires_at DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT '0',
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_oa_attachment_storage_id ON oa_attachment(storage_id);
CREATE INDEX idx_oa_attachment_process ON oa_attachment(process_id, status);
CREATE INDEX idx_oa_attachment_pending_ttl ON oa_attachment(status, process_id, create_time);
CREATE INDEX idx_oa_attachment_owner_draft ON oa_attachment(owner_user_id, draft_id, status);
