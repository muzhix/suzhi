CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('active', 'disabled')),
    platform_role VARCHAR(16) NOT NULL CHECK (platform_role IN ('user', 'admin')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE document (
    id UUID PRIMARY KEY,
    title VARCHAR(512) NOT NULL,
    authors TEXT,
    material_type VARCHAR(64),
    created_by UUID NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE document_acl (
    document_id UUID NOT NULL REFERENCES document (id),
    user_id UUID NOT NULL REFERENCES app_user (id),
    role VARCHAR(16) NOT NULL CHECK (role IN ('viewer', 'editor', 'owner')),
    PRIMARY KEY (document_id, user_id)
);

CREATE TABLE project (
    id UUID PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    created_by UUID NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE project_member (
    project_id UUID NOT NULL REFERENCES project (id),
    user_id UUID NOT NULL REFERENCES app_user (id),
    role VARCHAR(16) NOT NULL CHECK (role IN ('viewer', 'editor', 'owner')),
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE project_document (
    project_id UUID NOT NULL REFERENCES project (id),
    document_id UUID NOT NULL REFERENCES document (id),
    document_version_id UUID,
    PRIMARY KEY (project_id, document_id)
);
