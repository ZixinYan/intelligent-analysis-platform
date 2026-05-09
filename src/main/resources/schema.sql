use intelligent_analysis_platform;
CREATE TABLE IF NOT EXISTS query_execution (
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
CREATE INDEX idx_query_execution_tenant_status ON query_execution (tenant_id, status);
CREATE INDEX idx_query_execution_tenant_ds ON query_execution (tenant_id, datasource_id);

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
CREATE INDEX idx_async_task_tenant_status ON async_task (tenant_id, status);

CREATE TABLE IF NOT EXISTS task_result (
    task_id VARCHAR(64) PRIMARY KEY,
    result_json varchar(2048) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow_definition (
    workflow_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workflow_name VARCHAR(256) NOT NULL,
    definition_json VARCHAR(2048) NOT NULL,
    operator_id VARCHAR(64),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE INDEX idx_workflow_definition_tenant_updated ON workflow_definition (tenant_id, updated_at);
CREATE UNIQUE INDEX uq_workflow_definition_id_tenant ON workflow_definition (workflow_id, tenant_id);

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
CREATE INDEX idx_ds_config_tenant ON datasource_config (tenant_id);
CREATE INDEX idx_ds_config_tenant_name ON datasource_config (tenant_id, name);
