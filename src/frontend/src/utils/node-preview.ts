import type { NodeMetaDTO } from '@/types/contract'

const categoryIcons: Record<string, string> = {
  QUERY: 'Q',
  COMPUTE: 'C',
  OUTPUT: 'O',
  GOVERNANCE: 'G',
}

export function createDefaultNodeConfig(meta?: NodeMetaDTO) {
  return { ...(meta?.defaults ?? {}) }
}

export function resolveNodeIcon(meta?: NodeMetaDTO) {
  if (meta?.icon) {
    return meta.icon
  }
  return categoryIcons[meta?.category ?? 'QUERY'] ?? 'N'
}

export function buildNodePreview(meta?: NodeMetaDTO, config: Record<string, unknown> = {}) {
  if (!meta) {
    return ['未绑定元数据']
  }
  if (meta.nodeType === 'sql_query') {
    const sql = String(config.sqlTemplate ?? '').trim()
    const datasource = String(config.datasourceId ?? '未选择数据源')
    return [datasource, sql ? sql.split('\n')[0].slice(0, 64) : '未填写 SQL']
  }
  if (meta.nodeType === 'chart_output') {
    return [String(config.chartType ?? 'line'), String(config.xField ?? '未选择 X 轴')]
  }
  if (meta.nodeType === 'table_output') {
    const columns = Array.isArray(config.columns) ? config.columns.join(', ') : '未选择列'
    return ['table', columns]
  }
  return [meta.description ?? meta.displayName]
}
