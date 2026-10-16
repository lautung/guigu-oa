-- A publication is server-owned evidence. Existing published rows are intentionally not backfilled:
-- their original artifact and exact Flowable definition cannot be proven from current state alone.
CREATE TABLE oa_process_template_publication (
  id BIGINT NOT NULL AUTO_INCREMENT,
  process_template_id BIGINT NOT NULL,
  artifact_path VARCHAR(512) NOT NULL,
  artifact_sha256 CHAR(64) NOT NULL,
  process_definition_key VARCHAR(100) NOT NULL,
  deployment_id VARCHAR(64),
  process_definition_id VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_oa_process_template_publication_template UNIQUE (process_template_id)
);
