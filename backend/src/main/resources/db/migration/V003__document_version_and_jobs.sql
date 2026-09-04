CREATE TABLE asset (
    id UUID PRIMARY KEY,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    original_filename VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE upload_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user (id),
    document_id UUID REFERENCES document (id),
    object_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    declared_content_type VARCHAR(128) NOT NULL,
    declared_size_bytes BIGINT NOT NULL,
    declared_checksum_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('pending', 'completed', 'failed')),
    asset_id UUID REFERENCES asset (id),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE document_version (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document (id),
    asset_id UUID NOT NULL REFERENCES asset (id),
    version_no INT NOT NULL,
    content_fingerprint VARCHAR(64) NOT NULL,
    parser_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (document_id, version_no)
);

CREATE TABLE text_unit (
    id UUID PRIMARY KEY,
    document_version_id UUID NOT NULL REFERENCES document_version (id),
    seq INT NOT NULL,
    path VARCHAR(512),
    display_text TEXT NOT NULL,
    page_no INT,
    UNIQUE (document_version_id, seq),
    UNIQUE (id, document_version_id)
);

ALTER TABLE project_document
    ADD CONSTRAINT project_document_version_fk
    FOREIGN KEY (document_version_id) REFERENCES document_version (id);

CREATE TABLE job (
    id UUID PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('pending', 'running', 'succeeded', 'partial', 'failed', 'cancelled')),
    stage VARCHAR(64),
    progress INT,
    total INT,
    attempt_count INT NOT NULL DEFAULT 0,
    next_run_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    worker_id VARCHAR(64),
    idempotency_key VARCHAR(256) NOT NULL UNIQUE,
    document_id UUID REFERENCES document (id),
    document_version_id UUID REFERENCES document_version (id),
    created_by UUID NOT NULL REFERENCES app_user (id),
    error_summary TEXT,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX job_claim_idx ON job (status, next_run_at);

CREATE TABLE processing_run (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES job (id),
    input_document_version_id UUID REFERENCES document_version (id),
    input_range TEXT,
    context_strategy VARCHAR(64),
    provider VARCHAR(64),
    model_id VARCHAR(128),
    prompt_version VARCHAR(64),
    output_schema_version VARCHAR(64),
    parameters JSONB,
    token_input INT,
    token_output INT,
    cost_usd NUMERIC(12, 6),
    latency_ms INT,
    status VARCHAR(16) NOT NULL CHECK (status IN ('running', 'succeeded', 'failed')),
    review_model_id VARCHAR(128),
    review_prompt_version VARCHAR(64),
    review_result_summary JSONB,
    attachment_object_key VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ
);
