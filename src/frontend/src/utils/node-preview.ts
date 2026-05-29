import { normalizeNodeType } from '@/constants/analysis-nodes'
import type { NodeMetaDTO } from '@/types/contract'

const categoryIcons: Record<string, string> = {
  QUERY:      '🔍',
  COMPUTE:    '⚙️',
  OUTPUT:     '📊',
  GOVERNANCE: '🛡️',
  ANALYSIS:   '📐',
}

const nodeTypeIcons: Record<string, string> = {
  'analysis-sql-query':           '🔍',
  'analysis-aggregate':           '∑',
  'analysis-time-series-compute': '📈',
  'analysis-pivot':               '⊞',
  'analysis-chart-output':        '📊',
  'analysis-table-output':        '📋',
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
  condition:           '⎇',
}

function getCodeNodeLanguage(type: string) {
  if (type === 'python_script') return 'Python'
  if (type === 'java_code') return 'Java'
  return 'Code'
}

function getCodeNodeSource(config: Record<string, unknown>) {
  const candidates = [config.code, config.script]
  return candidates.find(value => typeof value === 'string' && value.trim()) as string | undefined
}

function getCodeNodeInputs(config: Record<string, unknown>) {
  const candidates = [config.inputVars, config.inputs, config.inputVariables]
  for (const value of candidates) {
    if (Array.isArray(value)) {
      const names = value
        .map((item) => {
          if (typeof item === 'string') return item
          if (item && typeof item === 'object') {
            const record = item as Record<string, unknown>
            return String(record.name ?? record.key ?? record.field ?? '').trim()
          }
          return ''
        })
        .filter(Boolean)
      if (names.length) return names
    }
  }
  return []
}

function getCodeNodeOutputs(config: Record<string, unknown>) {
  const candidates = [config.outputs, config.outputVars, config.outputVar, config.resultVar]
  for (const value of candidates) {
    if (Array.isArray(value)) {
      const names = value
        .map((item) => {
          if (typeof item === 'string') return item
          if (item && typeof item === 'object') {
            const record = item as Record<string, unknown>
            return String(record.name ?? record.key ?? record.field ?? '').trim()
          }
          return ''
        })
        .filter(Boolean)
      if (names.length) return names
    }
    if (value && typeof value === 'object') {
      const names = Object.keys(value as Record<string, unknown>).filter(Boolean)
      if (names.length) return names
    }
    if (typeof value === 'string' && value.trim()) {
      return [value.trim()]
    }
  }
  return []
}

function getCodeNodeSummary(code?: string) {
  if (!code) return '未填写代码'
  const line = code.split('\n').map(item => item.trim()).find(Boolean)
  return line ? line.slice(0, 60) : '未填写代码'
}

function buildCodeNodePreview(type: string, config: Record<string, unknown>) {
  const language = getCodeNodeLanguage(type)
  const inputs = getCodeNodeInputs(config)
  const outputs = getCodeNodeOutputs(config)
  const code = getCodeNodeSource(config)
  return [
    `语言: ${language}`,
    `输入: ${inputs.length ? inputs.slice(0, 3).join(', ') : '未配置'}`,
    `输出: ${outputs.length ? outputs.slice(0, 3).join(', ') : '未声明'}`,
    `摘要: ${getCodeNodeSummary(code)}`,
  ]
}

export function createDefaultNodeConfig(meta?: NodeMetaDTO) {
  return { ...(meta?.defaults ?? {}) }
}

export function resolveNodeIcon(meta?: NodeMetaDTO, explicitType?: string) {
  const nodeType = explicitType || meta?.nodeType
  if (meta?.icon) return meta.icon
  if (nodeType && nodeTypeIcons[nodeType]) return nodeTypeIcons[nodeType]
  return categoryIcons[meta?.category ?? 'QUERY'] ?? '📦'
}

export function buildNodePreview(meta?: NodeMetaDTO, config: Record<string, unknown> = {}, explicitType?: string): string[] {
  if (!meta && !explicitType) return ['未绑定元数据']

  const type = normalizeNodeType(explicitType || meta?.nodeType || '')

  switch (type) {
    case 'sql_query': {
      const ds = config.datasourceId ? `数据源: ${config.datasourceId}` : '未选择数据源'
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
      const rowDim = String(config.rowField ?? '未设置行维度')
      const colDim = String(config.colField ?? '未设置列维度')
      const valFld = String(config.valueField ?? '未设置值字段')
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
      const col = String(config.outputField ?? '新列')
      return [`新列: ${col}`, expr ? expr.slice(0, 50) : '未填写表达式']
    }
    case 'python_script':
    case 'java_code': {
      return buildCodeNodePreview(type, config)
    }
    case 'chart_output': {
      const chartType = String(config.chartType ?? 'LINE')
      const x = String(config.xField ?? '未选择 X 轴')
      const y = String(config.yField ?? '未选择 Y 轴')
      return [`图表: ${chartType}`, `X: ${x}  Y: ${y}`]
    }
    case 'table_output': {
      const cols = Array.isArray(config.columns) ? config.columns.join(', ') : '全部列'
      const page = config.pageable !== false ? '分页' : '不分页'
      return [`列: ${cols.slice(0, 50)}`, page]
    }
    case 'condition': {
      const conditions = Array.isArray(config.conditions) ? `${config.conditions.length} 条分支条件` : '未设置条件'
      return [conditions]
    }
    default:
      return meta?.description ? [meta.description.split(/[；，]/)[0].slice(0, 60)] : [meta?.displayName ?? explicitType ?? '未命名节点']
  }
}
