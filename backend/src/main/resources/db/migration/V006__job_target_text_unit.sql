ALTER TABLE job
    ADD COLUMN target_text_unit_id UUID;

ALTER TABLE job
    ADD CONSTRAINT job_target_text_unit_fk
    FOREIGN KEY (target_text_unit_id, document_version_id)
    REFERENCES text_unit (id, document_version_id);
