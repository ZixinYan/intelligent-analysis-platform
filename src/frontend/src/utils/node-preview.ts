import type { NodeMetaDTO } from '@/types/contract'
import { normalizeNodeType } from '@/constants/analysis-nodes'

const categoryIcons: Record<string, string> = {
  QUERY:      '🔍',
  COMPUTE:    '⚙️',
  OUTPUT:     '📊',
  GOVERNANCE: '🛡️',
  ANALYSIS:   '📐',
}

const nodeTypeIcons: Record<string, string> = {
  // 新版 analysis- 前缀（Phase 2 规范）
  'analysis-sql-query':           '🔍',
  'analysis-aggregate':           '∑',
  'analysis-time-series-compute': '📈',
  'analysis-pivot':               '⊞',
  'analysis-chart-output':        '📊',
  'analysis-table-output':        '📋',
  // 旧版 snake_case（向后兼容）
  sql_query:           '🔍',
  aggregate:           '∑',
  time_series_compute: '📈',
  pivot:               '⊞',
  filter:              '🔽',
  sort:                '↕',
  formula:             'ƒ',
  python_script:       '🐍',
  java_code:           '☕',
  chart_output:        '📊',
  table_output:        '📋',
}

export function createDefaultNodeConfig(meta?: NodeMetaDTO) {
  return { ...(meta?.defaults ?? {}) }
}

export function resolveNodeIcon(meta?: NodeMetaDTO) {
  if (meta?.icon) return meta.icon
  if (meta?.nodeType && nodeTypeIcons[meta.nodeType]) return nodeTypeIcons[meta.nodeType]
  return categoryIcons[meta?.category ?? 'QUERY'] ?? '📦'
}

export function buildNodePreview(meta?: NodeMetaDTO, config: Record<string, unknown> = {}): string[] {
  if (!meta) return ['未绑定元数据']

  // 统一规范化节点类型：analysis-sql-query → sql_query，sql_query → sql_query
  const type = normalizeNodeType(meta.nodeType)

  switch (type) {
    case 'sql_query': {
      const ds  = config.datasourceId ? `数据源: ${config.datasourceId}` : '未选择数据源'
      const sql = String(config.sqlTemplate ?? '').trim()
      const sqlLine = sql ? sql.split('\n')[0].slice(0, 60) : '未填写 SQL'
      return [ds, sqlLine]
    }
    case 'aggregate': {
      const dims = Array.isArray(config.groupByFields) ? config.groupByFields.join(', ') : '未设置维度'
      const metrics = Array.isArray(config.metrics) ? `${config.metrics.length} 个指标` : '未设置指标'
      return [`维度: ${dims}`, `聚合: ${metrics}`]
    }
    case 'time_series_compute': {
      const mode = String(config.computeMode ?? '未选择')
      const grain = String(config.timeGranularity ?? '未选择')
      return [`模式: ${mode}`, `时间粒度: ${grain}`]
    }
    case 'pivot': {
      const rowDim  = String(config.rowField  ?? '未设置行维度')
      const colDim  = String(config.colField  ?? '未设置列维度')
      const valFld  = String(config.valueField ?? '未设置值字段')
      return [`行: ${rowDim}  列: ${colDim}`, `值: ${valFld}`]
    }
    case 'filter': {
      const conditions = Array.isArray(config.conditions) ? `${config.conditions.length} 条筛选条件` : '未设置条件'
      return [conditions]
    }
    case 'sort': {
      const sorts = Array.isArray(config.sortOrders) ? `${config.sortOrders.length} 个排序字段` : '未设置排序'
      return [sorts]
    }
    case 'formula': {
      const expr = String(config.expression ?? '').trim()
      const col  = String(config.outputField ?? '新列')
      return [`新列: ${col}`, expr ? expr.slice(0, 50) : '未填写表达式']
    }
    case 'python_script': {
      const lines = String(config.script ?? '').split('\n').filter(Boolean)
      return lines.length ? [`${lines.length} 行脚本`, lines[0].slice(0, 55)] : ['未填写脚本']
    }
    case 'java_code': {
      const lines = String(config.code ?? '').split('\n').filter(Boolean)
      return lines.length ? [`${lines.length} 行代码`, lines[0].slice(0, 55)] : ['未填写代码']
    }
    case 'chart_output': {
      const chartType = String(config.chartType ?? 'line')
      const x    = String(config.xField ?? '未选择 X 轴')
      const y    = String(config.yField ?? '未选择 Y 轴')
      return [`图表: ${chartType}`, `X: ${x}  Y: ${y}`]
    }
    case 'table_output': {
      const cols = Array.isArray(config.columns) ? config.columns.join(', ') : '全部列'
      const page = config.pageable !== false ? '分页' : '不分页'
      return [`列: ${cols.slice(0, 50)}`, page]
    }
    default:
      return meta.description ? [meta.description.split(/[；，]/)[0].slice(0, 60)] : [meta.displayName]
  }
}
