export type ValueType =
  | 'STRING'
  | 'LONG'
  | 'DOUBLE'
  | 'BOOLEAN'
  | 'DATE'
  | 'TIMESTAMP'
  | 'JSON'
  | 'UNKNOWN'

export interface RequestContextDTO {
  requestId: string
  tenantId: string
  userId: string
}

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export interface TableColumnDTO {
  field?: string
  label?: string
  format?: string
  sortable?: boolean
  name?: string
  valueType?: ValueType
  nullable?: boolean
  description?: string
}

export interface DataSourceConfigDTO {
  datasourceId: string
  datasourceName: string
  datasourceType: string
}

export interface DatasourceSummaryDTO {
  datasourceId: string
  datasourceName: string
  datasourceType: string
  description?: string
  createdAt?: number
  updatedAt?: number
}

export interface FieldSchemaDTO {
  fieldId?: string
  name: string
  valueType?: ValueType
  displayName?: string
  extensions?: Record<string, unknown>
}

export type ChartType = 'LINE' | 'BAR' | 'PIE' | 'SCATTER' | 'TABLE'

export interface NodePositionDTO {
  x: number
  y: number
}

export interface NodeTraceDTO {
  nodeId: string
  nodeName?: string
  status?: string
  startedAt?: number
  finishedAt?: number
  message?: string
}

export interface WorkflowPositionDTO {
  x: number
  y: number
}

export interface WorkflowNodeDTO {
  nodeId: string
  nodeType: string
  category?: string
  version?: string
  metadata?: NodeMetaDTO | Record<string, unknown>
  config?: Record<string, unknown>
}

export interface WorkflowEdgeDTO {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
  condition?: string
}

export interface WorkflowDefinitionDTO {
  workflowId: string
  workflowName: string
  nodes: WorkflowNodeDTO[]
  edges: WorkflowEdgeDTO[]
  positions: Record<string, WorkflowPositionDTO>
  createdAt?: number
  updatedAt?: number
  currentVersionId?: string
  currentVersionNumber?: number
  publishedVersionId?: string
  publishedVersionNumber?: number
}

export interface SqlNodeResultDTO {
  queryId?: string
  columns?: TableColumnDTO[]
  rows?: Array<Record<string, unknown>>
  rowCount?: number
}

export interface QueryExecutionSummaryDTO {
  queryId: string
  requestId?: string
  datasourceId?: string
  status?: string
  startedAt?: number
  finishedAt?: number
}

export interface QueryResultDTO extends QueryExecutionSummaryDTO {
  sql?: string
  dataset?: DatasetDTO
  result?: StandardResultDTO
  executionMeta?: {
    elapsedMs?: number
    durationMs?: number
    scannedRows?: number
    cacheHit?: boolean
  }
  columns?: TableColumnDTO[]
  rows?: Array<Record<string, unknown>>
  rowCount?: number
  errorCode?: string
  errorMessage?: string
}

export interface ValidateResultDTO {
  queryId: string
  valid: boolean
  normalizedSql?: string
  message?: string
  violationCodes?: string[]
}

export interface AsyncSubmitResponseDTO {
  taskId: string
  status?: string
  pollUrl?: string
}

export interface TriggerRunResultDTO {
  triggerId?: string
  runId?: string
  workflowId?: string
  status?: string
  message?: string
}

export interface CreateDatasourceRequestDTO {
  datasourceName: string
  datasourceType: string
  jdbcUrl: string
  username?: string
  password?: string
  description?: string
}

export interface UpdateDatasourceRequestDTO extends CreateDatasourceRequestDTO {}

export interface QueryRequestDTO {
  requestId?: string
  datasourceId: string
  sql: string
  limit?: number
  parameters?: Record<string, unknown> | Array<Record<string, unknown>>
  option?: {
    timeoutMs?: number
    limit?: number
    useCache?: boolean
  }
}

export interface TriggerDefinitionDTO {
  triggerId: string
  workflowId: string
  triggerType: TriggerType
  triggerStatus: TriggerStatus
  cronExpr?: string
  nextFireAt?: number
  webhookToken?: string
  webhookUrl?: string
  defaultInputs?: string
  lastFireAt?: number
  lastRunId?: string
  lastStatus?: string
  createdAt: number
  updatedAt: number
}

export interface TriggerWebhookResponseDTO {
  accepted: boolean
  triggerId?: string
  message?: string
}

export interface TriggerRunDetailDTO {
  runId: string
  triggerId: string
  workflowId?: string
  taskId?: string
  status?: string
  message?: string
  startedAt?: number
  finishedAt?: number
}

export interface NodeDefinitionDTO {
  nodeType: string
  displayName: string
  description?: string
  category?: string
  version?: string
}

export type FieldComponentType =
  | 'INPUT'
  | 'TEXTAREA'
  | 'SELECT'
  | 'MULTI_SELECT'
  | 'SWITCH'
  | 'NUMBER_INPUT'
  | 'SQL_EDITOR'
  | 'CODE_EDITOR'
  | 'FIELD_PICKER'
  | 'FIELD_MULTI_SELECTOR'
  | 'DATASOURCE_SELECT'
  | 'OUTPUT_VAR_LIST'

export type ConditionOperator = 'EQ' | 'NE' | 'IN' | 'NOT_IN' | 'GT' | 'LT' | 'CONTAINS' | 'IS_EMPTY' | 'IS_NOT_EMPTY'

export interface FieldRuleConditionDTO {
  watchField: string
  operator: ConditionOperator
  targetValues: string[]
  visible?: boolean
  enabled?: boolean
}

export interface FieldValidationRuleDTO {
  type: 'required' | 'min' | 'max' | 'minLength' | 'maxLength' | 'pattern'
  message?: string
  min?: number
  max?: number
  maxLength?: number
  pattern?: string
}

export interface PanelFieldOptionsSourceDTO {
  type?: 'static' | 'remote' | 'schema-fields'
  uri?: string
  labelField?: string
  valueField?: string
  acceptedCapabilities?: string[]
}

export interface VariableBindingConfigDTO {
  allowLiteral?: boolean
  enabled?: boolean
  bindingPathHint?: string
}

export interface PanelFieldDTO {
  field?: string
  fieldKey?: string
  label: string
  description?: string
  componentType?: FieldComponentType
  required?: boolean
  multiple?: boolean
  placeholder?: string
  helpText?: string
  visible?: boolean
  disabled?: boolean
  options?: OptionDTO[]
  optionsSource?: PanelFieldOptionsSourceDTO
  props?: Record<string, unknown>
  variableBinding?: VariableBindingConfigDTO
  visibilityRules?: FieldRuleConditionDTO[]
  enableRules?: FieldRuleConditionDTO[]
  validation?: FieldValidationRuleDTO
  validations?: FieldValidationRuleDTO[]
}

export interface OptionDTO {
  label: string
  value: string
}

export interface FieldCandidateSlotDTO {
  slotKey?: string
  slot?: string
  label?: string
  required?: boolean
  multiple?: boolean
  acceptedTypes?: string[]
  acceptedCapabilities?: string[]
  candidates?: Array<{ field: string, label?: string, score?: number }>
}

export interface MappingCandidateRequestDTO {
  nodeType: string
  renderer?: string
  upstreamFields?: SchemaFieldDTO[]
  schema?: SchemaInferResultDTO
}

export interface NodeCapabilityDTO {
  capabilityKey: string
  supported: boolean
  message?: string
}

export interface NodeMetaDTO {
  protocolVersion?: string
  metadataVersion?: string
  nodeType: string
  displayName: string
  description?: string
  category?: string
  version?: string
  nodeVersion?: string
  sortOrder?: number
  icon?: string
  defaults?: Record<string, unknown>
  configSchema?: NodeConfigSchemaDTO
  panelFields?: PanelFieldDTO[]
  capabilities?: NodeCapabilityDTO[]
  inputPorts?: Array<Record<string, unknown>>
  outputPorts?: Array<Record<string, unknown>>
  tags?: string[]
}

export interface DatasetSchemaDTO {
  fields?: SchemaFieldDTO[]
}

export interface DatasetDTO {
  datasetId?: string
  total?: number
  schema?: DatasetSchemaDTO
  columns?: Array<Record<string, unknown> | TableColumnDTO>
  rows?: Array<Record<string, unknown>>
  rowCount?: number
}

export interface PageResult<T> {
  items: T[]
  total: number
  pageNum?: number
  pageSize?: number
}

export type ExecutionStatus = 'PENDING' | 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export interface ErrorInfoDTO {
  code?: string
  message?: string
}

export interface AsyncTaskStatusDTO {
  taskId: string
  agentTaskId?: string
  taskType?: string
  requestId?: string
  status?: ExecutionStatus | string
  progress?: number
  dataset?: DatasetDTO
  result?: WorkflowRunResultDTO
  clarification?: AiClarificationQuestionDTO
  trace?: Record<string, unknown>
  confidence?: number
  error?: ErrorInfoDTO
  createdAt?: number
  updatedAt?: number
}

export type DatasourceType = 'MYSQL' | 'CLICKHOUSE' | 'POSTGRESQL' | 'HIVE' | 'UNKNOWN'

export interface DatasourceQueryRequestDTO {
  page?: number
  pageSize?: number
  keyword?: string
}

export interface WorkflowQueryRequestDTO {
  page?: number
  pageSize?: number
  keyword?: string
}

export interface WorkflowSaveRequestDTO {
  workflowId?: string
  workflowName: string
  nodes: WorkflowNodeDTO[]
  edges: WorkflowEdgeDTO[]
  positions: Record<string, WorkflowPositionDTO>
}

export interface WorkflowStreamEventDTO {
  eventType: 'node_start' | 'node_progress' | 'node_result' | 'workflow_done' | 'workflow_error'
           | 'iteration_started' | 'iteration_next' | 'iteration_finished'
  nodeId?: string
  /** nodeType，iteration_started 时携带 */
  nodeType?: string
  chunkIndex?: number
  rows?: Array<Record<string, unknown>>
  error?: ErrorInfoDTO
  status?: ExecutionStatus
  result?: StandardResultDTO
  meta?: {
    elapsedMs?: number
  }
  /** 迭代节点总迭代次数（iteration_started 携带） */
  iterationLength?: number
  /** 当前完成的迭代轮次索引，0-based（iteration_next 携带） */
  iterationIndex?: number
}

export interface DatasourceCreateRequestDTO {
  id?: string
  name?: string
  type?: string
  host?: string
  port?: number
  database?: string
  readonly?: boolean
  jdbcOptions?: Record<string, string>
  datasourceName?: string
  datasourceType?: string
  jdbcUrl?: string
  username?: string
  password?: string
  description?: string
}

export interface DatasourceUpdateRequestDTO extends DatasourceCreateRequestDTO {}

export interface DatasourceTestConnectionResultDTO {
  success: boolean
  message?: string
  latencyMs?: number
}

export interface StandardResultDTO {
  kind?: 'EMPTY' | 'DATASET' | 'TABLE' | 'CHART' | 'VARIABLES'
  dataset?: DatasetDTO
  chart?: ChartOutputDTO
  table?: TableOutputDTO
  variables?: Record<string, unknown>
  outputs?: Record<string, unknown>
}

export interface WorkflowRunRequestDTO {
  versionId?: string
  inputs?: Record<string, unknown>
  nodes?: WorkflowNodeDTO[]
  edges?: WorkflowEdgeDTO[]
}

export interface NodeResultEventDTO {
  eventType: 'node_result'
  nodeId: string
  status: ExecutionStatus
  result?: StandardResultDTO
  meta?: {
    elapsedMs?: number
  }
  error?: ErrorInfoDTO
}

export interface DatasourceDTO {
  id?: string
  datasourceId: string
  datasourceName: string
  datasourceType: string
  name?: string
  type?: string
  status?: string
  readonly?: boolean
  host?: string
  port?: number
  database?: string
  jdbcOptions?: Record<string, string>
  jdbcUrl?: string
  username?: string
  description?: string
  createdAt?: number
  updatedAt?: number
}

export interface WorkflowVersionDiffDTO {
  fromVersion: number
  toVersion: number
  addedNodeIds: string[]
  removedNodeIds: string[]
  modifiedNodeIds: string[]
  addedEdgeIds: string[]
  removedEdgeIds: string[]
}

export interface WorkflowVersionDTO {
  versionId: string
  workflowId: string
  versionNumber: number
  changeSummary?: string
  published?: boolean
  createdAt?: number
  definition?: WorkflowDefinitionDTO
}

export interface NodeConfigFieldDTO {
  field: string
  fieldKey?: string
  label: string
  order?: number
  componentType?: FieldComponentType
  required?: boolean
  defaultValue?: unknown
  visibilityRules?: FieldRuleConditionDTO[]
  enableRules?: FieldRuleConditionDTO[]
}

export interface PanelSectionDTO {
  key?: string
  title?: string
  order?: number
  fields: Array<PanelFieldDTO | NodeConfigFieldDTO>
}

export interface NodeConfigSectionDTO {
  key?: string
  title?: string
  order?: number
  fields?: NodeConfigFieldDTO[]
}

export interface NodeConfigSchemaDTO {
  schemaType?: string
  schemaVersion?: string
  panelId?: string
  fields?: PanelFieldDTO[]
  mappingSlots?: FieldCandidateSlotDTO[]
  sections?: NodeConfigSectionDTO[]
}

export interface TableOutputDTO {
  title?: string
  columns?: TableColumnDTO[]
  rows?: Array<Record<string, unknown>>
  rowCount?: number
  option?: {
    pageable?: boolean
    pageSize?: number
    downloadable?: boolean
  }
  meta?: {
    downloadable?: boolean
    partial?: boolean
    totalRows?: number
    returnedRows?: number
  }
}

export interface ChartSeriesDTO {
  name?: string
  data?: unknown[]
  stack?: string
  yAxis?: string
}

export interface ChartOutputDTO {
  title?: string
  chartType?: ChartType
  data?: {
    categories?: string[]
    series?: ChartSeriesDTO[]
  }
  option?: {
    legend?: boolean
    tooltip?: boolean
    extensions?: Record<string, unknown>
  }
  meta?: {
    partial?: boolean
  }
}

export interface SchemaFieldDTO {
  fieldId?: string
  name: string
  path?: string[]
  valueType?: ValueType
  nullable?: boolean
  displayName?: string
  capabilities?: string[]
  extensions?: Record<string, unknown>
}

export interface SchemaInferResultDTO {
  protocolVersion?: string
  schemaId?: string
  schemaVersion?: string
  kind?: string
  fields?: SchemaFieldDTO[]
}

export interface NodeResultDTO {
  nodeId?: string
  nodeType?: string
  status?: string
  success?: boolean
  message?: string
  error?: { code?: string, message?: string }
  meta?: { elapsedMs?: number }
  result?: StandardResultDTO
  columns?: TableColumnDTO[]
  rows?: Array<Record<string, unknown>>
  rowCount?: number
  outputs?: Record<string, unknown>
}

export interface NodeDebugRequestDTO {
  nodeId?: string
  node?: {
    nodeId?: string
    nodeType?: string
    config?: Record<string, unknown>
  }
  nodeType?: string
  config?: Record<string, unknown>
  upstreamOutputs?: Record<string, unknown>
  upstreamMockInputs?: Record<string, unknown>
}

export interface SaveWorkflowRequestDTO {
  workflowId?: string
  workflowName: string
  nodes: WorkflowNodeDTO[]
  edges: WorkflowEdgeDTO[]
  positions: Record<string, WorkflowPositionDTO>
}

export interface WorkflowVersionSummaryDTO {
  versionId: string
  workflowId: string
  versionNumber: number
  changeSummary?: string
  published?: boolean
  createdAt?: number
}

export interface WorkflowVersionDetailDTO extends WorkflowVersionSummaryDTO {
  definition?: WorkflowDefinitionDTO
}

export interface CreateVersionRequestDTO {
  workflowName?: string
  changeSummary?: string
  nodes: WorkflowNodeDTO[]
  edges: WorkflowEdgeDTO[]
  positions: Record<string, WorkflowPositionDTO>
}

export interface PublishVersionRequestDTO {
  versionId?: string
}

export interface ExecuteWorkflowRequestDTO {
  versionId?: string
  inputs?: Record<string, unknown>
}

export interface WorkflowRunResultDTO {
  workflowId?: string
  versionId?: string
  status?: string
  outputs?: Record<string, unknown>
  traces?: NodeTraceDTO[]
  variables?: Record<string, unknown>
}

export interface WorkflowExecutionResultDTO {
  workflowId?: string
  versionId?: string
  status?: string
  outputs?: Record<string, unknown>
  traces?: NodeTraceDTO[]
}

export interface DryRunNodeDTO {
  nodeId: string
  nodeType: string
  config?: Record<string, unknown>
}

export interface DryRunRequestDTO {
  workflowId?: string
  nodes: DryRunNodeDTO[]
  edges?: WorkflowEdgeDTO[]
}

export interface DryRunResultDTO {
  success: boolean
  traces?: NodeTraceDTO[]
  message?: string
}

export interface AiChatRequestDTO {
  prompt: string
  conversationId?: string
  history?: Array<{ role: string, content: string }>
}

export interface AiSqlRequestDTO {
  datasourceId: string
  tableName: string
  description: string
  conversationId?: string
  knowledgeBaseId?: string
}

export interface AiChartRecommendRequestDTO {
  fields: FieldSchemaDTO[]
  context?: string
}

export interface ChartRecommendationDTO {
  chartType: ChartType
  confidence: number
  reason: string
  fieldMapping?: Record<string, string>
}

export type AiWorkflowBuildMode = 'DRAFT_ONLY' | 'RUN_AND_SAVE' | 'AGENT'
export type AiWorkflowBuildResponseMode = 'LEGACY_DRAFT' | 'ENVELOPE'
export type AiWorkflowBuildResponseType = 'DRAFT' | 'CLARIFICATION' | 'TASK_ACCEPTED'

export interface AiClarificationAnswerDTO {
  key: string
  value: string
}

export interface AiClarificationQuestionDTO {
  key: string
  label: string
  required?: boolean
  hint?: string
  inputType?: string
  options?: Array<Record<string, string>>
}

export interface AiWorkflowBuildRequestDTO {
  datasourceId: string
  description: string
  workflowName?: string
  buildMode?: AiWorkflowBuildMode
  responseMode?: AiWorkflowBuildResponseMode
  agentTaskId?: string
  runAndSave?: boolean
  conversationId?: string
  clarificationAnswers?: AiClarificationAnswerDTO[]
}

export interface AiWorkflowSaveRequestDTO {
  workflowId: string
}

export interface AiWorkflowSaveResultDTO {
  workflowId?: string
  versionId?: string
  saved?: boolean
}

export interface AiWorkflowLoadResultDTO {
  workflowId?: string
  workflowName?: string
  draft?: WorkflowDefinitionDTO
}

export interface AiWorkflowExecuteRequestDTO {
  workflowId: string
  inputs?: Record<string, unknown>
}

export interface AiWorkflowDryRunRequestDTO {
  workflowId: string
  inputs?: Record<string, unknown>
}

export interface AiWorkflowDryRunResultDTO {
  supported?: boolean
  message?: string
  draft?: WorkflowDefinitionDTO
  execution?: AiWorkflowExecuteResultDTO
}

export interface AiWorkflowExecuteResultDTO {
  supported: boolean
  status?: string
  message?: string
  workflowId?: string
  runId?: string
  finalResult?: StandardResultDTO
  finalResultNodeId?: string
  datasetId?: string
}

export interface AiWorkflowBuildResultDTO {
  responseType: AiWorkflowBuildResponseType
  buildMode: AiWorkflowBuildMode
  draft?: WorkflowDefinitionDTO
  agentTaskId?: string
  clarifications?: AiClarificationQuestionDTO[]
  saved?: boolean
  workflowId?: string
  datasetId?: string
  execution?: AiWorkflowExecuteResultDTO
  metadata?: Record<string, unknown>
}

export type TriggerType = 'CRON' | 'WEBHOOK'
export type TriggerStatus = 'ACTIVE' | 'PAUSED' | 'DELETED'

export interface TriggerDTO {
  triggerId: string
  workflowId: string
  triggerType: TriggerType
  triggerStatus: TriggerStatus
  cronExpr?: string
  nextFireAt?: number
  webhookToken?: string
  webhookUrl?: string
  defaultInputs?: string
  lastFireAt?: number
  lastRunId?: string
  lastStatus?: string
  createdAt: number
  updatedAt: number
}

export interface CreateTriggerRequestDTO {
  triggerType: TriggerType
  cronExpr?: string
  defaultInputs?: string
  secretKey?: string
  context?: RequestContextDTO
}

export interface SavedDatasetSummaryDTO {
  datasetId: string
  tenantId?: string
  name: string
  description?: string
  createdBy?: string
  rowCount?: number
  sourceWorkflowId?: string
  sourceNodeId?: string
  createdAt: number
  updatedAt: number
}

export interface SavedDatasetDetailDTO extends SavedDatasetSummaryDTO {
  schema?: Record<string, unknown>
  columns?: TableColumnDTO[]
  rows?: Array<Record<string, unknown>>
}

export interface ExportFileDTO {
  fileId: string
  tenantId?: string
  fileName: string
  format: string
  fileSizeBytes?: number
  rowCount?: number
  createdAt: number
  expiresAt?: number
}

export interface TriggerExportRequestDTO {
  datasetId: string
  format: 'csv' | 'xlsx' | 'json'
  fileName?: string
}

