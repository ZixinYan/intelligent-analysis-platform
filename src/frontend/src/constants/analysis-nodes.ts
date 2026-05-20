/**
 * 分析节点类型常量（对应后端 NodeMetaDTO.nodeType）
 *
 * 命名规范：analysis-{功能}，与后端 API /api/v1/node-definitions 返回的 nodeType 一致。
 * 兼容旧版 snake_case 别名（sql_query、aggregate 等），保留于 LEGACY_NODE_TYPES。
 */
export const ANALYSIS_NODE_TYPES = {
  SQL_QUERY:    'analysis-sql-query',
  AGGREGATE:    'analysis-aggregate',
  TIME_SERIES:  'analysis-time-series-compute',
  PIVOT:        'analysis-pivot',
  CHART_OUTPUT: 'analysis-chart-output',
  TABLE_OUTPUT: 'analysis-table-output',
} as const

export type AnalysisNodeType = typeof ANALYSIS_NODE_TYPES[keyof typeof ANALYSIS_NODE_TYPES]

/** 旧版 snake_case 节点类型（向后兼容） */
export const LEGACY_ANALYSIS_NODE_TYPES = {
  SQL_QUERY:    'sql_query',
  AGGREGATE:    'aggregate',
  TIME_SERIES:  'time_series_compute',
  PIVOT:        'pivot',
  CHART_OUTPUT: 'chart_output',
  TABLE_OUTPUT: 'table_output',
} as const

export type LegacyAnalysisNodeType = typeof LEGACY_ANALYSIS_NODE_TYPES[keyof typeof LEGACY_ANALYSIS_NODE_TYPES]

/** 节点所属分析类别标识（对应 NodeMetaDTO.category） */
export const ANALYSIS_CATEGORY = 'ANALYSIS'

const RAW_NODE_TYPE_BY_BUSINESS: Record<string, string> = {
  [LEGACY_ANALYSIS_NODE_TYPES.SQL_QUERY]: ANALYSIS_NODE_TYPES.SQL_QUERY,
  [LEGACY_ANALYSIS_NODE_TYPES.AGGREGATE]: ANALYSIS_NODE_TYPES.AGGREGATE,
  [LEGACY_ANALYSIS_NODE_TYPES.TIME_SERIES]: ANALYSIS_NODE_TYPES.TIME_SERIES,
  [LEGACY_ANALYSIS_NODE_TYPES.PIVOT]: ANALYSIS_NODE_TYPES.PIVOT,
  [LEGACY_ANALYSIS_NODE_TYPES.CHART_OUTPUT]: ANALYSIS_NODE_TYPES.CHART_OUTPUT,
  [LEGACY_ANALYSIS_NODE_TYPES.TABLE_OUTPUT]: ANALYSIS_NODE_TYPES.TABLE_OUTPUT,
}

/** 所有分析节点类型（新版 + 旧版） */
export const ALL_ANALYSIS_NODE_TYPES: string[] = [
  ...Object.values(ANALYSIS_NODE_TYPES),
  ...Object.values(LEGACY_ANALYSIS_NODE_TYPES),
]

/** 将 analysis- 前缀的类型名规范化为 snake_case 别名（用于 switch-case 复用） */
export function normalizeNodeType(nodeType: string): string {
  return nodeType
    .replace(/^analysis-/, '')
    .replace(/-/g, '_')
}

export function toRawNodeType(nodeType: string): string {
  if (!nodeType) {
    return ''
  }
  if (nodeType.startsWith('analysis-')) {
    return nodeType
  }
  return RAW_NODE_TYPE_BY_BUSINESS[nodeType] ?? nodeType
}
