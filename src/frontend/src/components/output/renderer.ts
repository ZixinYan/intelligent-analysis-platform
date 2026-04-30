import type {
  ChartOutputDTO,
  ChartSeriesDTO,
  ChartType,
  DatasetDTO,
  QueryResultDTO,
  StandardResultDTO,
  TableColumnDTO,
  TableOutputDTO,
} from '@/types/contract'

export type RendererMode = 'preview' | 'runtime'
export type RendererKind = 'chart' | 'table' | 'empty'

export interface RendererFallback {
  title: string
  description: string
}

export interface RendererNotice {
  tone: 'info' | 'warning'
  message: string
}

export interface ChartRendererModel {
  kind: 'chart'
  title: string
  chartType: ChartType
  categories: string[]
  series: Array<{
    name: string
    data: unknown[]
    stack?: string
    yAxis?: string
  }>
  optionExtensions: Record<string, unknown>
  showLegend: boolean
  showTooltip: boolean
  mode: RendererMode
  partial: boolean
  empty: boolean
  fallback?: RendererFallback
  notices: RendererNotice[]
}

export interface TableRendererModel {
  kind: 'table'
  title: string
  columns: Array<{
    key: string
    label: string
    format?: string
    sortable?: boolean
  }>
  rows: Array<Record<string, unknown>>
  mode: RendererMode
  pageable: boolean
  pageSize: number
  downloadable: boolean
  partial: boolean
  empty: boolean
  fallback?: RendererFallback
  notices: RendererNotice[]
}

export interface EmptyRendererModel {
  kind: 'empty'
  title: string
  mode: RendererMode
  fallback: RendererFallback
  notices: RendererNotice[]
}

export type RendererModel = ChartRendererModel | TableRendererModel | EmptyRendererModel

function hasRows(rows?: Array<Record<string, unknown>>) {
  return Array.isArray(rows) && rows.length > 0
}

function normalizeTitle(title: string | undefined, fallback: string) {
  return title?.trim() || fallback
}

function buildNotices(partial: boolean, mode: RendererMode, emptyMessage?: string) {
  const notices: RendererNotice[] = []
  if (mode === 'preview') {
    notices.push({ tone: 'info', message: '预览模式仅展示最小结果，不代表最终看板样式。' })
  }
  if (partial) {
    notices.push({ tone: 'warning', message: '当前结果已截断，建议进入运行态查看完整数据。' })
  }
  if (emptyMessage) {
    notices.push({ tone: 'warning', message: emptyMessage })
  }
  return notices
}

function normalizeChartSeries(series?: ChartSeriesDTO[]) {
  return (series ?? []).map(item => ({
    name: item.name?.trim() || '未命名序列',
    data: Array.isArray(item.data) ? item.data : [],
    stack: item.stack,
    yAxis: item.yAxis,
  }))
}

function datasetColumnsFromRows(rows: Array<Record<string, unknown>>) {
  const firstRow = rows[0]
  return Object.keys(firstRow ?? {}).map<TableColumnDTO>(key => ({
    field: key,
    label: key,
  }))
}

function tableFromDataset(dataset?: DatasetDTO): TableOutputDTO | undefined {
  if (!dataset) {
    return undefined
  }
  const rows = dataset.rows ?? []
  const inferredColumns = datasetColumnsFromRows(rows)
  const mappedColumns = Array.isArray(dataset.columns) && dataset.columns.length
    ? dataset.columns.map<TableColumnDTO>((column) => ({
        field: String(column.field ?? column.name ?? column.label ?? ''),
        label: String(column.label ?? column.name ?? column.field ?? ''),
        format: typeof column.format === 'string' ? column.format : undefined,
      })).filter(column => column.field)
    : []
  const columns = mappedColumns.length ? mappedColumns : inferredColumns
  return {
    title: '查询结果',
    columns,
    rows,
    option: {
      pageable: true,
      pageSize: Math.min(rows.length || 20, 20),
      downloadable: false,
    },
    meta: {
      partial: typeof dataset.total === 'number' ? dataset.total > rows.length : false,
      totalRows: dataset.total,
      returnedRows: rows.length,
    },
  }
}

function resolveStandardResult(input?: StandardResultDTO | QueryResultDTO): StandardResultDTO | undefined {
  if (!input) {
    return undefined
  }
  if ('result' in input && input.result) {
    return input.result
  }
  if ('kind' in input || 'chart' in input || 'table' in input || 'dataset' in input) {
    return input as StandardResultDTO
  }
  return undefined
}

function buildChartModel(chart: ChartOutputDTO, mode: RendererMode): ChartRendererModel {
  const chartType = chart.chartType ?? 'LINE'
  const categories = chart.data?.categories ?? []
  const series = normalizeChartSeries(chart.data?.series)
  const partial = Boolean(chart.meta?.partial)
  const empty = series.length === 0 || series.every(item => item.data.length === 0)
  const fallback = empty
    ? {
        title: '暂无图表数据',
        description: '图表协议已返回，但缺少可渲染的数据点。',
      }
    : undefined

  return {
    kind: 'chart',
    title: normalizeTitle(chart.title, '图表结果'),
    chartType,
    categories,
    series,
    optionExtensions: chart.option?.extensions ?? {},
    showLegend: chart.option?.legend !== false,
    showTooltip: chart.option?.tooltip !== false,
    mode,
    partial,
    empty,
    fallback,
    notices: buildNotices(partial, mode, empty ? '图表数据不足，已自动降级为空状态。' : undefined),
  }
}

function buildTableModel(table: TableOutputDTO, mode: RendererMode): TableRendererModel {
  const rows = table.rows ?? []
  const columns = (table.columns ?? []).map((column) => ({
    key: column.field,
    label: column.label?.trim() || column.field,
    format: column.format,
    sortable: Boolean(column.sortable),
  }))
  const partial = Boolean(table.meta?.partial)
  const empty = !hasRows(rows)
  const fallback = empty
    ? {
        title: '暂无表格数据',
        description: '表格协议已返回，但没有可展示的行数据。',
      }
    : undefined

  return {
    kind: 'table',
    title: normalizeTitle(table.title, '表格结果'),
    columns,
    rows,
    mode,
    pageable: Boolean(table.option?.pageable),
    pageSize: table.option?.pageSize && table.option.pageSize > 0 ? table.option.pageSize : 20,
    downloadable: Boolean(table.option?.downloadable ?? table.meta?.downloadable),
    partial,
    empty,
    fallback,
    notices: buildNotices(partial, mode, empty ? '表格数据为空，已自动降级为空状态。' : undefined),
  }
}

export function resolveRendererModel(payload: StandardResultDTO | QueryResultDTO | undefined, mode: RendererMode = 'runtime'): RendererModel {
  const result = resolveStandardResult(payload)
  if (!result) {
    return {
      kind: 'empty',
      title: '暂无结果',
      mode,
      fallback: {
        title: '暂无可渲染结果',
        description: '后端尚未返回 chart/table/dataset payload。',
      },
      notices: buildNotices(false, mode),
    }
  }

  if (result.chart) {
    return buildChartModel(result.chart, mode)
  }

  if (result.table) {
    return buildTableModel(result.table, mode)
  }

  if (result.dataset) {
    return buildTableModel(tableFromDataset(result.dataset) ?? { rows: [] }, mode)
  }

  return {
    kind: 'empty',
    title: '空结果',
    mode,
    fallback: {
      title: '结果不可视化',
      description: result.kind === 'VARIABLES' ? '当前结果为变量集，暂不支持可视化渲染。' : '当前结果类型未命中 chart/table 渲染器。',
    },
    notices: buildNotices(false, mode),
  }
}
