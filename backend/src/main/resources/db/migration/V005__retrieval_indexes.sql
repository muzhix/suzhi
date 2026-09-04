CREATE INDEX text_unit_trgm_idx ON text_unit USING gin (display_text gin_trgm_ops);
CREATE INDEX edu_text_trgm_idx ON edu USING gin (text gin_trgm_ops);
CREATE INDEX edu_version_status_idx ON edu (document_version_id, status);
