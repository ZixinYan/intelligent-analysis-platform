-- ============================================================
-- Helper: conditionally create index (MySQL, JDBC-safe via separator: //)
-- ============================================================
CREATE PROCEDURE IF NOT EXISTS try_create_index(
    IN idx_name VARCHAR(64),
    IN tbl_name VARCHAR(64),
    IN col_list VARCHAR(256),
    IN unique_flag BOOLEAN
)
BEGIN
    DECLARE idx_count INT DEFAULT 0;
    SELECT COUNT(*) INTO idx_count
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = tbl_name
          AND index_name = idx_name;
    IF idx_count = 0 THEN
        IF unique_flag THEN
            SET @ddl = CONCAT('CREATE UNIQUE INDEX ', idx_name, ' ON ', tbl_name, ' (', col_list, ')');
        ELSE
            SET @ddl = CONCAT('CREATE INDEX ', idx_name, ' ON ', tbl_name, ' (', col_list, ')');
        END IF;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

-- Helper: conditionally add a column (MySQL 8.x compatible)
CREATE PROCEDURE IF NOT EXISTS try_add_column(
    IN tbl_name VARCHAR(64),
    IN col_name VARCHAR(64),
    IN col_def  VARCHAR(256)
)
BEGIN
    DECLARE col_count INT DEFAULT 0;
    SELECT COUNT(*) INTO col_count
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name  = tbl_name
          AND column_name = col_name;
    IF col_count = 0 THEN
        SET @ddl = CONCAT('ALTER TABLE ', tbl_name, ' ADD COLUMN ', col_name, ' ', col_def);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

-- ============================================================
-- Tables
-- ============================================================
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
) //

CALL try_create_index('idx_query_execution_tenant_status', 'query_execution', 'tenant_id, status', FALSE) //
CALL try_create_index('idx_query_execution_tenant_ds', 'query_execution', 'tenant_id, datasource_id', FALSE) //

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
) //

CALL try_create_index('idx_async_task_tenant_status', 'async_task', 'tenant_id, status', FALSE) //

CREATE TABLE IF NOT EXISTS task_result (
    task_id VARCHAR(64) PRIMARY KEY,
    result_json varchar(2048) NOT NULL,
    created_at BIGINT NOT NULL
) //

CREATE TABLE IF NOT EXISTS workflow_definition (
    workflow_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workflow_name VARCHAR(256) NOT NULL,
    definition_json VARCHAR(2048) NOT NULL,
    operator_id VARCHAR(64),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
) //

CALL try_create_index('idx_workflow_definition_tenant_updated', 'workflow_definition', 'tenant_id, updated_at', FALSE) //
CALL try_create_index('uq_workflow_definition_id_tenant', 'workflow_definition', 'workflow_id, tenant_id', TRUE) //

-- Version tracking columns (added by Phase 4)
CALL try_add_column('workflow_definition', 'current_version_id',   'VARCHAR(64)') //
CALL try_add_column('workflow_definition', 'published_version_id', 'VARCHAR(64)') //

CREATE TABLE IF NOT EXISTS workflow_version (
    version_id      VARCHAR(64) PRIMARY KEY,
    workflow_id     VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    version_number  INT NOT NULL,
    definition_json MEDIUMTEXT NOT NULL,
    change_summary  VARCHAR(1000),
    published       BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(64),
    created_at      BIGINT NOT NULL
) //

CALL try_create_index('idx_wf_version_workflow_num', 'workflow_version',
    'workflow_id, version_number', FALSE) //
CALL try_create_index('idx_wf_version_tenant_published', 'workflow_version',
    'tenant_id, workflow_id, published', FALSE) //

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
) //

CALL try_create_index('idx_ds_config_tenant', 'datasource_config', 'tenant_id', FALSE) //
CALL try_create_index('idx_ds_config_tenant_name', 'datasource_config', 'tenant_id, name', FALSE) //

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
) //

CALL try_create_index('idx_approval_tenant_status', 'approval_request', 'tenant_id, status', FALSE) //
CALL try_create_index('idx_approval_workflow_node', 'approval_request', 'workflow_id, node_id', FALSE) //

CREATE TABLE IF NOT EXISTS saved_dataset (
    dataset_id          VARCHAR(64) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    description         VARCHAR(1000),
    created_by          VARCHAR(64),
    schema_json         TEXT NOT NULL,
    stat_json           TEXT,
    rows_json           MEDIUMTEXT,
    source_workflow_id  VARCHAR(64),
    source_node_id      VARCHAR(64),
    created_at          BIGINT NOT NULL,
    updated_at          BIGINT NOT NULL
) //

CALL try_create_index('idx_saved_dataset_tenant_updated', 'saved_dataset', 'tenant_id, updated_at', FALSE) //

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
) //

CALL try_create_index('idx_export_file_tenant', 'export_file', 'tenant_id', FALSE) //
CALL try_create_index('idx_export_file_expires', 'export_file', 'expires_at', FALSE) //

-- ============================================================
-- Phase 8: 工作流执行记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS workflow_run_log (
    run_id          VARCHAR(64) PRIMARY KEY,
    workflow_id     VARCHAR(64) NOT NULL,
    version_id      VARCHAR(64),
    tenant_id       VARCHAR(64) NOT NULL,
    trigger_type    VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    node_count      INT,
    started_at      BIGINT NOT NULL,
    finished_at     BIGINT,
    elapsed_ms      BIGINT,
    node_trace_json MEDIUMTEXT,
    created_by      VARCHAR(64)
) //

CALL try_create_index('idx_run_log_workflow_started', 'workflow_run_log',
    'workflow_id, started_at', FALSE) //
CALL try_create_index('idx_run_log_tenant_status', 'workflow_run_log',
    'tenant_id, status, started_at', FALSE) //

-- Clean up
DROP PROCEDURE IF EXISTS try_create_index //
DROP PROCEDURE IF EXISTS try_add_column //
