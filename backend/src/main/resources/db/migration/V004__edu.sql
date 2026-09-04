CREATE TABLE edu (
    id UUID PRIMARY KEY,
    document_version_id UUID NOT NULL REFERENCES document_version (id),
    type VARCHAR(16) NOT NULL CHECK (type IN ('EVENT', 'STATE', 'RELATION', 'EVALUATION', 'RULE')),
    text TEXT NOT NULL,
    predicate VARCHAR(128) NOT NULL,
    time_value VARCHAR(128),
    time_source_form VARCHAR(128),
    time_precision VARCHAR(16),
    status VARCHAR(16) NOT NULL CHECK (status IN ('proposed', 'active', 'rejected', 'superseded')),
    revision BIGINT NOT NULL DEFAULT 1,
    location_precision VARCHAR(16) NOT NULL CHECK (location_precision IN ('exact', 'unit', 'page', 'failed')),
    used_external_knowledge BOOLEAN NOT NULL DEFAULT FALSE,
    review_result VARCHAR(16),
    review_notes TEXT,
    processing_run_id UUID REFERENCES processing_run (id),
    supersedes_edu_id UUID REFERENCES edu (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, document_version_id)
);

CREATE TABLE edu_source_ref (
    id UUID PRIMARY KEY,
    edu_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    text_unit_id UUID NOT NULL,
    quote TEXT,
    char_start INT,
    char_end INT,
    precision VARCHAR(16) NOT NULL CHECK (precision IN ('exact', 'unit', 'page')),
    purpose VARCHAR(16) NOT NULL CHECK (purpose IN ('primary', 'context')),
    FOREIGN KEY (edu_id, document_version_id) REFERENCES edu (id, document_version_id),
    FOREIGN KEY (text_unit_id, document_version_id) REFERENCES text_unit (id, document_version_id)
);

CREATE TABLE edu_argument (
    id UUID PRIMARY KEY,
    edu_id UUID NOT NULL REFERENCES edu (id),
    role VARCHAR(32) NOT NULL CHECK (role IN (
        'subject', 'object', 'participant', 'location', 'origin',
        'destination', 'instrument', 'value', 'other'
    )),
    role_name VARCHAR(128),
    value TEXT NOT NULL,
    source_form TEXT,
    entity_type VARCHAR(32)
);
