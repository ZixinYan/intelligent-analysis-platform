export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  success: boolean
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export type NodeCategory = 'QUERY' | 'COMPUTE' | 'OUTPUT' | 'GOVERNANCE'
export type ResultKind = 'DATASET' | 'TABLE' | 'CHART' | 'VARIABLES' | 'EMPTY'
export type ChartType = 'LINE' | 'BAR' | 'PIE' | 'SCATTER' | 'AREA' | 'MIXED'
export type ValueType =
  | 'STRING'
  | 'INTEGER'
  | 'LONG'
  | 'DECIMAL'
  | 'BOOLEAN'
  | 'DATE'
  | 'DATETIME'
  | 'OBJECT'
  | 'DATASET'
  | 'CHART'
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
export type ExecutionStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
export type ConditionOperator = 'EQ' | 'NE' | 'IN' | 'NOT_IN' | 'GT' | 'LT' | 'CONTAINS' | 'IS_EMPTY' | 'IS_NOT_EMPTY'

export interface OptionDTO {
  label: string
  value: string
}

export interface OptionsSourceDTO {
  type: 'remote' | 'schema-fields' | 'static'
  uri?: string
  method?: string
  valueField?: string
  labelField?: string
  source?: string
  acceptedCapabilities?: string[]
}

export interface ValidationRuleDTO {
  type: string
  message?: string
  min?: number
  max?: number
  maxLength?: number
}

export interface VariableBindingSupportDTO {
  enabled: boolean
  allowLiteral: boolean
  bindingPathHint?: string
}

export interface FieldVisibilityRuleDTO {
  watchField: string
  operator: ConditionOperator
  targetValues: string[]
  visible: boolean
}

export interface FieldEnableRuleDTO {
  watchField: string
  operator: ConditionOperator
  targetValues: string[]
  enabled: boolean
}

export interface PanelFieldDTO {
  field: string
  label: string
  componentType: FieldComponentType
  required?: boolean
  visible?: boolean
  editable?: boolean
  disabled?: boolean
  multiple?: boolean
  order?: number
  valueType?: ValueType
  semanticType?: string
  defaultValue?: unknown
  placeholder?: string
  description?: string
  options?: OptionDTO[]
  optionsSource?: OptionsSourceDTO
  validations?: ValidationRuleDTO[]
  validation?: ValidationRuleDTO
  variableBinding?: VariableBindingSupportDTO
  visibilityRules?: FieldVisibilityRuleDTO[]
  enableRules?: FieldEnableRuleDTO[]
  props?: Record<string, unknown>
  extensions?: Record<string, unknown>
}

export interface PanelSectionDTO {
  key: string
  title: string
  order: number
  fields: PanelFieldDTO[]
}

export interface PanelRuleDTO {
  field?: string
  ruleType?: string
  props?: Record<string, unknown>
}

export interface NodeConfigSchemaDTO {
  schemaType: string
  schemaVersion: string
  panelId: string
  layout?: Record<string, unknown>
  sections: PanelSectionDTO[]
  rules?: PanelRuleDTO[]
  extensions?: Record<string, unknown>
}

export interface NodePortMetaDTO {
  name: string
  label: string
  valueType: ValueType
  required: boolean
  multiple?: boolean
  description?: string
}

export interface NodeCapabilityDTO {
  code: string
  name: string
  capabilityConfig?: Record<string, unknown>
  params?: Record<string, unknown>
}

export interface NodeMetaDTO {
  protocolVersion: string
  metadataVersion: string
  nodeType: string
  nodeVersion: string
  displayName: string
  category: NodeCategory
  sortOrder?: number
  icon?: string
  description?: string
  helpLink?: string
  singleton?: boolean
  startNode?: boolean
  endNode?: boolean
  tags?: string[]
  configSchema?: NodeConfigSchemaDTO
  inputPorts?: NodePortMetaDTO[]
  outputPorts?: NodePortMetaDTO[]
  capabilities?: NodeCapabilityDTO[]
  defaults?: Record<string, unknown>
  extensions?: Record<string, unknown>
}

export interface FieldSchemaDTO {
  fieldId: string
  name: string
  path: string[]
  valueType: ValueType
  nullable: boolean
  displayName: string
  semanticType?: string
  capabilities?: string[]
  sampleValues?: unknown[]
  stats?: Record<string, unknown>
}

export interface MappingHintsDTO {
  chart?: Record<string, string[]>
  table?: Record<string, string[]>
}

export interface SchemaInferResultDTO {
  protocolVersion: string
  schemaId: string
  schemaVersion: string
  kind: string
  summary?: Record<string, unknown>
  fields: FieldSchemaDTO[]
  mappingHints?: MappingHintsDTO
  rawSchema?: Record<string, unknown>
}

export interface FieldMappingCandidateDTO {
  field: string
  score: number
  reason?: string
}

export interface MappingCandidateRequestDTO {
  renderer?: string
  upstreamFields?: FieldSchemaDTO[]
}

export interface FieldCandidateSlotDTO {
  slot: string
  required: boolean
  acceptedTypes: string[]
  acceptedCapabilities: string[]
  candidates: FieldMappingCandidateDTO[]
}

export interface RequestContextDTO {
  tenantId: string
  userId: string
  requestId?: string | null
}

export interface QueryOptionDTO {
  timeoutMs?: number
  limit?: number
  useCache?: boolean
  /** 流式分块大小（行数），默认 500 */
  chunkSize?: number
  /** 是否启用 SSE 流式推送 */
  streaming?: boolean
}

// ---------------------------------------------------------------------------
// 流式执行 SSE 事件类型
// ---------------------------------------------------------------------------

export interface NodeStartEventDTO {
  eventType: 'node_start'
  runId: string
  nodeId: string
  nodeType: string
  startedAt: number
}

export interface NodeProgressEventDTO {
  eventType: 'node_progress'
  runId: string
  nodeId: string
  chunkIndex: number
  totalChunks?: number
  rows: Record<string, unknown>[]
}

export interface NodeResultEventDTO {
  eventType: 'node_result'
  runId: string
  nodeId: string
  status: string
  result?: StandardResultDTO
  meta?: NodeRunMetaDTO
}

export interface WorkflowDoneEventDTO {
  eventType: 'workflow_done'
  runId: string
  workflowId: string
  status: string
  elapsedMs: number
}

export interface WorkflowErrorEventDTO {
  eventType: 'workflow_error'
  runId: string
  workflowId: string
  error: ErrorInfoDTO
}

export type WorkflowStreamEventDTO =
  | NodeStartEventDTO
  | NodeProgressEventDTO
  | NodeResultEventDTO
  | WorkflowDoneEventDTO
  | WorkflowErrorEventDTO

export interface QueryParameterDTO {
  name?: string
  type?: string
  value?: unknown
}

export interface BaseNodeConfigDTO {
  [key: string]: unknown
}

export interface SqlQueryNodeConfigDTO extends BaseNodeConfigDTO {
  datasourceId?: string
  sqlTemplate?: string
  parameters?: QueryParameterDTO[]
  queryOption?: QueryOptionDTO
}

export type JoinType = 'INNER' | 'LEFT' | 'RIGHT' | 'FULL'

export interface JoinCondition {
  leftField: string
  rightField: string
}

export interface DataJoinNodeConfigDTO extends BaseNodeConfigDTO {
  leftDatasetRef?: string
  rightDatasetRef?: string
  joinType?: JoinType
  on?: JoinCondition[]
  selectColumns?: string[]
  memoryRowLimit?: number
}

export interface QueryRequestDTO {
  requestId?: string
  datasourceId: string
  sql: string
  parameters?: Record<string, unknown>
  option?: QueryOptionDTO
  context?: RequestContextDTO
}

export interface ErrorInfoDTO {
  code?: string
  message?: string
  details?: Record<string, unknown>
}

export interface QueryExecutionMetaDTO {
  durationMs?: number
  affectedRows?: number
  cacheHit?: boolean
}

export interface NodeRunMetaDTO {
  nodeId?: string
  nodeType?: string
  elapsedMs?: number
  cached?: boolean
  taskId?: string
  summary?: Record<string, unknown>
}

export interface NodeResultDTO {
  nodeId?: string
  nodeType?: string
  status: ExecutionStatus
  result?: StandardResultDTO
  error?: ErrorInfoDTO
  meta?: NodeRunMetaDTO
}

export interface NodeDebugRequestDTO {
  workflowId?: string
  nodeId?: string
  node: WorkflowNodeDTO
  upstreamMockInputs?: Record<string, unknown>
  context?: RequestContextDTO
}

export interface OutputMetaDTO {
  sourceNodeId?: string
  generatedAt?: string
  downloadable?: boolean
  partial?: boolean
  totalRows?: number
  returnedRows?: number
  truncationStrategy?: string
}

export interface ChartSeriesDTO {
  name?: string
  stack?: string
  data?: unknown[]
  yAxis?: string
}

export interface ChartDataDTO {
  categories?: string[]
  series?: ChartSeriesDTO[]
}

export interface ChartOptionDTO {
  legend?: boolean
  tooltip?: boolean
  extensions?: Record<string, unknown>
}

export interface ChartOutputDTO {
  title?: string
  chartType?: ChartType
  data?: ChartDataDTO
  option?: ChartOptionDTO
  meta?: OutputMetaDTO
}

export interface TableColumnDTO {
  field: string
  label?: string
  valueType?: ValueType
  format?: string
  sortable?: boolean
}

export interface TableOptionDTO {
  pageable?: boolean
  downloadable?: boolean
  pageSize?: number
}

export interface TableOutputDTO {
  title?: string
  columns?: TableColumnDTO[]
  rows?: Array<Record<string, unknown>>
  option?: TableOptionDTO
  meta?: OutputMetaDTO
}

export interface StandardResultDTO {
  kind?: ResultKind
  dataset?: DatasetDTO
  table?: TableOutputDTO
  chart?: ChartOutputDTO
  variables?: Record<string, unknown>
}

export interface DatasetDTO {
  columns?: Array<Record<string, unknown>>
  rows?: Array<Record<string, unknown>>
  schema?: {
    fields?: FieldSchemaDTO[]
  }
  total?: number
  /** 产生此数据集的原始 SQL（下推时作为子查询使用） */
  sourceSql?: string
  /** 来源数据源 ID（必须与下游算子一致才可下推） */
  sourceDatasourceId?: string
}

export interface QueryResultDTO {
  queryId: string
  status: ExecutionStatus
  dataset?: DatasetDTO
  result?: StandardResultDTO
  executionMeta?: QueryExecutionMetaDTO
  computeMeta?: NodeRunMetaDTO
  error?: ErrorInfoDTO
}

export interface ValidateResultDTO {
  queryId?: string
  valid: boolean
  normalizedSql?: string
  sqlFingerprint?: string
  violationCodes?: string[]
  message?: string
  validatedAt?: number
}

export interface WorkflowNodeDTO {
  nodeId: string
  nodeType: string
  category?: NodeCategory
  version?: string
  metadata?: NodeMetaDTO
  config?: BaseNodeConfigDTO
}

export interface WorkflowEdgeDTO {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
  condition?: 'true' | 'false' | null   // null = 无条件边（向后兼容）
}

export interface WorkflowPositionDTO {
  x?: number
  y?: number
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

export interface WorkflowVersionDTO {
  versionId: string
  workflowId: string
  versionNumber: number
  changeSummary?: string
  published: boolean
  createdBy?: string
  createdAt: number
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

export interface WorkflowSaveRequestDTO {
  workflowName: string
  nodes: WorkflowNodeDTO[]
  edges: WorkflowEdgeDTO[]
  positions: Record<string, WorkflowPositionDTO>
  context?: RequestContextDTO
}

export interface WorkflowQueryRequestDTO {
  page?: number
  pageSize?: number
  context?: RequestContextDTO
}

export interface WorkflowRunRequestDTO {
  workflowId?: string
  nodes: WorkflowNodeDTO[]
  edges?: WorkflowEdgeDTO[]
  inputs?: Record<string, unknown>
  context?: RequestContextDTO
}

export interface WorkflowRunResultDTO {
  outputs?: Record<string, StandardResultDTO>
}

export interface AsyncTaskStatusDTO {
  taskId: string
  taskType: string
  status: ExecutionStatus
  progress?: number
  dataset?: DatasetDTO
  result?: WorkflowRunResultDTO
  error?: ErrorInfoDTO
  createdAt?: number
  updatedAt?: number
}

export interface AsyncSubmitResponseDTO {
  taskId: string
  status: ExecutionStatus
}

export type DatasourceType = 'MYSQL' | 'CLICKHOUSE' | 'POSTGRES'
export type DatasourceStatus = 'ACTIVE' | 'INACTIVE' | 'UNREACHABLE'

export interface DatasourceDTO {
  id: string
  tenantId?: string
  name: string
  type: DatasourceType
  host: string
  port: number
  database: string
  username: string
  jdbcOptions?: Record<string, string>
  status?: DatasourceStatus
  readonly?: boolean
  createdAt?: number
  updatedAt?: number
  createdBy?: string
}

export interface DatasourceCreateRequestDTO {
  name: string
  type: DatasourceType
  host: string
  port: number
  database: string
  username: string
  password: string
  jdbcOptions?: Record<string, string>
  readonly?: boolean
  context?: RequestContextDTO
}

export interface DatasourceUpdateRequestDTO extends Partial<DatasourceCreateRequestDTO> {}

export interface DatasourceQueryRequestDTO {
  type?: string
  keyword?: string
  page?: number
  pageSize?: number
  context?: RequestContextDTO
}

export interface DatasourceTestConnectionResultDTO {
  success: boolean
  latencyMs?: number
  message?: string
  serverVersion?: string
}

// ---------------------------------------------------------------------------
// Phase 8: 工作流执行历史
// ---------------------------------------------------------------------------

export interface NodeTraceDTO {
  nodeId: string
  nodeType: string
  status: string
  elapsedMs?: number
  cached?: boolean
  rowCount?: number
  pushdown?: boolean
  error?: string
}

export interface WorkflowRunLogDTO {
  runId: string
  workflowId: string
  versionId?: string
  tenantId?: string
  status: string
  triggerType: string
  nodeCount?: number
  startedAt: number
  finishedAt?: number
  elapsedMs?: number
  createdBy?: string
  nodeTraces?: NodeTraceDTO[]
}

// ---------------------------------------------------------------------------
// Phase 10: AI 辅助工作流
// ---------------------------------------------------------------------------

export interface AiSqlRequestDTO {
  datasourceId: string
  tableName: string
  description: string
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

export interface AiWorkflowBuildRequestDTO {
  datasourceId: string
  description: string
  workflowName?: string
}

// ---------------------------------------------------------------------------
// 触发器（Trigger）
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// 已保存数据集（SavedDataset）
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// 导出（Export）
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// 运维指标（Ops）
// ---------------------------------------------------------------------------

export interface SlowQueryDTO {
  sql: string
  elapsedMs: number
  count: number
}

export interface OpsMetricsSummaryDTO {
  activeQueryCount: number
  todayQueryCount: number
  avgElapsedMs: number
  errorRate: number
  slowQueryTop?: SlowQueryDTO[]
}

export interface ActiveQueryDTO {
  queryId: string
  datasourceId: string
  sql: string
  startedAt: number
}

