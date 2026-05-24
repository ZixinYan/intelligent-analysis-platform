-- ============================================================
-- Schema for app runtime and tests
-- ============================================================

CREATE TABLE IF NOT EXISTS "query_execution" (
    query_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    datasource_id VARCHAR(64) NOT NULL,
    sql_fingerprint VARCHAR(64),
    mode VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at BIGINT,
    finished_at BIGINT,
    elapsed_ms BIGINT,
    cached BOOLEAN DEFAULT FALSE,
    truncated BOOLEAN DEFAULT FALSE,
    row_count INT DEFAULT 0,
    error_code VARCHAR(64),
    error_message VARCHAR(2000),
    operator_id VARCHAR(64),
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_query_execution_tenant_status ON "query_execution" (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_query_execution_tenant_ds ON "query_execution" (tenant_id, datasource_id);

CREATE TABLE IF NOT EXISTS async_task (
    task_id VARCHAR(64) PRIMARY KEY,
    task_type VARCHAR(32) NOT NULL,
    ref_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    operator_id VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    error_code VARCHAR(64),
    error_message VARCHAR(2000),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_async_task_tenant_status ON async_task (tenant_id, status);

CREATE TABLE IF NOT EXISTS task_result (
    task_id VARCHAR(64) PRIMARY KEY,
    result_json CLOB NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow_definition (
    workflow_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workflow_name VARCHAR(256) NOT NULL,
    definition_json CLOB NOT NULL,
    operator_id VARCHAR(64),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    current_version_id VARCHAR(64),
    published_version_id VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_workflow_definition_tenant_updated ON workflow_definition (tenant_id, updated_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_definition_id_tenant ON workflow_definition (workflow_id, tenant_id);

CREATE TABLE IF NOT EXISTS workflow_version (
    version_id      VARCHAR(64) PRIMARY KEY,
    workflow_id     VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    version_number  INT NOT NULL,
    definition_json CLOB NOT NULL,
    change_summary  VARCHAR(1000),
    published       BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(64),
    created_at      BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wf_version_workflow_num ON workflow_version (workflow_id, version_number);
CREATE INDEX IF NOT EXISTS idx_wf_version_tenant_published ON workflow_version (tenant_id, workflow_id, published);

CREATE TABLE IF NOT EXISTS datasource_config (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    type VARCHAR(32) NOT NULL,
    host VARCHAR(256) NOT NULL,
    port INT NOT NULL,
    database_name VARCHAR(256) NOT NULL,
    username VARCHAR(256) NOT NULL,
    encrypted_password VARCHAR(512) NOT NULL,
    jdbc_options VARCHAR(2048),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    readonly_flag BOOLEAN DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    created_by VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_ds_config_tenant ON datasource_config (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ds_config_tenant_name ON datasource_config (tenant_id, name);

CREATE TABLE IF NOT EXISTS approval_request (
    request_id      VARCHAR(64) PRIMARY KEY,
    workflow_id     VARCHAR(64) NOT NULL,
    node_id         VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    reason          VARCHAR(2000),
    approvers_json  VARCHAR(2048),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decided_by      VARCHAR(64),
    decision_comment VARCHAR(1000),
    created_at      BIGINT NOT NULL,
    decided_at      BIGINT,
    expires_at      BIGINT
);

CREATE INDEX IF NOT EXISTS idx_approval_tenant_status ON approval_request (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_approval_workflow_node ON approval_request (workflow_id, node_id);

CREATE TABLE IF NOT EXISTS saved_dataset (
    dataset_id          VARCHAR(64) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    description         VARCHAR(1000),
    created_by          VARCHAR(64),
    schema_json         CLOB NOT NULL,
    stat_json           CLOB,
    rows_json           CLOB,
    source_workflow_id  VARCHAR(64),
    source_node_id      VARCHAR(64),
    created_at          BIGINT NOT NULL,
    updated_at          BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_saved_dataset_tenant_updated ON saved_dataset (tenant_id, updated_at);

CREATE TABLE IF NOT EXISTS export_file (
    file_id         VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    file_name       VARCHAR(512) NOT NULL,
    format          VARCHAR(16) NOT NULL,
    storage_path    VARCHAR(1024) NOT NULL,
    file_size_bytes BIGINT,
    row_count       INT,
    created_at      BIGINT NOT NULL,
    expires_at      BIGINT
);

CREATE INDEX IF NOT EXISTS idx_export_file_tenant ON export_file (tenant_id);
CREATE INDEX IF NOT EXISTS idx_export_file_expires ON export_file (expires_at);

CREATE TABLE IF NOT EXISTS ai_conversation (
    conversation_id VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64),
    topic           VARCHAR(256),
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_conv_tenant_updated ON ai_conversation (tenant_id, updated_at);

CREATE TABLE IF NOT EXISTS ai_conversation_message (
    message_id       VARCHAR(64) PRIMARY KEY,
    conversation_id  VARCHAR(64) NOT NULL,
    role             VARCHAR(16) NOT NULL,
    content          CLOB NOT NULL,
    estimated_tokens INT,
    created_at       BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_conv_msg_conv ON ai_conversation_message (conversation_id, created_at);

CREATE TABLE IF NOT EXISTS knowledge_base (
    id          VARCHAR(64) PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description CLOB,
    created_at  BIGINT NOT NULL,
    updated_at  BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_knowledge_base_tenant ON knowledge_base (tenant_id);

CREATE TABLE IF NOT EXISTS workflow_trigger (
    id              VARCHAR(64) PRIMARY KEY,
    workflow_id     VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    trigger_type    VARCHAR(32) NOT NULL,
    trigger_status  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    cron_expr       VARCHAR(128),
    next_fire_at    BIGINT,
    webhook_token   VARCHAR(128),
    secret_key      VARCHAR(256),
    default_inputs  CLOB,
    last_fire_at    BIGINT,
    last_run_id     VARCHAR(64),
    last_status     VARCHAR(32),
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trigger_type_status ON workflow_trigger (trigger_type, trigger_status);
CREATE INDEX IF NOT EXISTS idx_trigger_webhook_token ON workflow_trigger (webhook_token);
CREATE INDEX IF NOT EXISTS idx_trigger_workflow_id ON workflow_trigger (workflow_id);
