import { computed, ref, type MaybeRefOrGetter, toValue } from 'vue'
import { getMappingCandidatesWithFields } from '@/api/node-definition'
import { getBusinessNodeType } from '@/adapters/workflow-graph'
import { useWorkflowStore } from '@/stores/workflow'
import type { FieldCandidateSlotDTO, NodeConfigSchemaDTO, PanelFieldDTO } from '@/types/contract'
import type { WorkflowNode } from '@/types/workflow'

function needsMappingCandidates(schema?: NodeConfigSchemaDTO) {
  return schema?.sections
    ?.flatMap(section => section.fields ?? [])
    .some(field => Boolean(field) && (field.componentType === 'FIELD_PICKER' || field.componentType === 'FIELD_MULTI_SELECTOR')) ?? false
}

/** 从上游 schema 直接构造 candidateSlots，无需后端 API */
function buildSlotsFromUpstreamSchema(
  schema: NodeConfigSchemaDTO,
  upstreamFieldNames: string[],
): FieldCandidateSlotDTO[] {
  const pickerFields = (schema.sections
    ?.flatMap(s => s.fields ?? []) as PanelFieldDTO[] | undefined)
    ?.filter(f => Boolean(f) && (f.componentType === 'FIELD_PICKER' || f.componentType === 'FIELD_MULTI_SELECTOR'))
  if (!pickerFields?.length) return []

  const candidates = upstreamFieldNames.map(name => ({ field: name, label: name, score: 1 }))
  return pickerFields.map(f => ({
    slot: f.field ?? f.fieldKey ?? '',
    required: f.required,
    candidates,
  }))
}

/** 从上游节点推断可用字段名列表（支持 schema、debugResult、SQL 配置） */
function inferUpstreamFieldNames(upstream: WorkflowNode): string[] {
  // 1. 优先使用已推断的 schema
  if (upstream.data.schema?.fields?.length) {
    return upstream.data.schema.fields.map(f => f.name ?? f.fieldId ?? '').filter(Boolean)
  }
  // 2. 从 debugResult 的 dataset 推断
  const dataset = upstream.data.debugResult?.result?.dataset
  if (dataset) {
    if (dataset.schema?.fields?.length) {
      return dataset.schema.fields.map((f: { name?: string; fieldId?: string }) => f.name ?? f.fieldId ?? '').filter(Boolean)
    }
    const rows = dataset.rows ?? []
    if (rows.length > 0) return Object.keys(rows[0])
    const columns = dataset.columns ?? []
    if (columns.length > 0) return columns.map((c: { name?: string; field?: string } | string) => typeof c === 'string' ? c : (c.name ?? c.field ?? '')).filter(Boolean)
  }
  // 3. 从 SQL SELECT 子句静态解析列别名
  const sqlTemplate = String(upstream.data.config?.sqlTemplate ?? '')
  if (sqlTemplate.trim()) {
    const parsed = parseSqlSelectColumns(sqlTemplate)
    if (parsed.length) return parsed
  }
  return []
}

/** 简单解析 SQL SELECT 子句的列名/别名 */
function parseSqlSelectColumns(sql: string): string[] {
  const match = sql.match(/SELECT\s+([\s\S]+?)\s+FROM\s/i)
  if (!match) return []
  const colsPart = match[1]
  if (colsPart.trim() === '*') return []
  return colsPart.split(',').map((col) => {
    col = col.trim()
    // 处理 `expr AS alias` 或 `table.col`
    const asMatch = col.match(/\bAS\s+[`"']?(\w+)[`"']?\s*$/i)
    if (asMatch) return asMatch[1]
    // 取最后一个词（可能是 col 或 table.col）
    const lastWord = col.replace(/[`"']/g, '').split(/[\s.]+/).filter(Boolean).pop() ?? ''
    return lastWord
  }).filter(name => name && /^\w+$/.test(name))
}

export function useMappingCandidates(
  nodeRef: MaybeRefOrGetter<WorkflowNode | undefined>,
  schemaRef?: MaybeRefOrGetter<NodeConfigSchemaDTO | undefined>,
) {
  const workflow = useWorkflowStore()
  const candidateSlots = ref<FieldCandidateSlotDTO[]>([])
  const loading = ref(false)
  let requestId = 0

  async function loadCandidates() {
    const node = toValue(nodeRef)
    const schema = toValue(schemaRef) ?? node?.data.meta?.configSchema
    if (!node || !needsMappingCandidates(schema)) {
      candidateSlots.value = []
      return
    }
    const upstream = workflow.getUpstreamNode(node.id)
    if (!upstream) {
      candidateSlots.value = []
      return
    }

    const upstreamFieldNames = inferUpstreamFieldNames(upstream)

    // 如果推断不出字段（上游完全没运行也没 SQL），清空候选
    if (!upstreamFieldNames.length) {
      candidateSlots.value = []
      return
    }

    // 当上游 schema 存在时，尝试后端 API（chart/table 节点需要智能排序）
    if (upstream.data.schema?.fields?.length) {
      loading.value = true
      const currentRequestId = ++requestId
      try {
        const businessNodeType = getBusinessNodeType(node)
        if (businessNodeType) {
          const renderer = businessNodeType === 'table_output'
            ? 'table'
            : String(node.data.config.chartType ?? businessNodeType ?? 'default')
          try {
            const result = await getMappingCandidatesWithFields(businessNodeType, {
              nodeType: businessNodeType,
              renderer,
              upstreamFields: upstream.data.schema.fields,
            })
            if (currentRequestId === requestId) {
              candidateSlots.value = result
              return
            }
          }
          catch {
            // 后端 API 失败，回退到前端直接推断
          }
        }
      }
      finally {
        if (currentRequestId === requestId) loading.value = false
      }
    }

    // 直接在前端从 schema 的 FIELD_PICKER 字段构造候选
    if (schema) {
      candidateSlots.value = buildSlotsFromUpstreamSchema(schema, upstreamFieldNames)
    }
  }

  return {
    candidateSlots: computed(() => candidateSlots.value),
    loading: computed(() => loading.value),
    loadCandidates,
  }
}

