import type { AppIconName } from '@/components/icons/AppIcon.vue'
import type { NodeMetaDTO } from '@/types/contract'

const categoryIcons: Record<string, AppIconName> = {
  QUERY: 'search',
  COMPUTE: 'analysis',
  OUTPUT: 'chart',
  GOVERNANCE: 'shield',
  ANALYSIS: 'analysis',
}

const nodeTypeIcons: Record<string, AppIconName> = {
  'analysis-sql-query': 'search',
  'analysis-aggregate': 'sigma',
  'analysis-time-series-compute': 'trend',
  'analysis-pivot': 'pivot',
  'analysis-chart-output': 'chart',
  'analysis-table-output': 'table',
  sql_query: 'search',
  aggregate: 'sigma',
  time_series_compute: 'trend',
  pivot: 'pivot',
  filter: 'filter',
  sort: 'sort',
  formula: 'function',
  python_script: 'code',
  java_code: 'code',
  chart_output: 'chart',
  table_output: 'table',
  condition: 'branch',
  iteration: 'loop',
}

export function resolveNodeIconName(meta?: NodeMetaDTO, explicitType?: string): AppIconName {
  const nodeType = explicitType || meta?.nodeType
  const icon = typeof meta?.icon === 'string' ? meta.icon.trim() : ''
  if (icon && icon in nodeTypeIcons) return nodeTypeIcons[icon]
  if (nodeType && nodeTypeIcons[nodeType]) return nodeTypeIcons[nodeType]
  return categoryIcons[meta?.category ?? 'QUERY'] ?? 'package'
}
