import { computed, reactive, ref, watch, type MaybeRefOrGetter, toValue } from 'vue'
import { getNodeDefinition } from '@/api/node-definition'
import { buildQueryRequest } from '@/composables/useNodeDebug'
import { inferQuerySchema } from '@/api/query'
import { useMappingCandidates } from '@/composables/useMappingCandidates'
import { useWorkflowStore, useWorkflowGraphStore } from '@/stores/workflow'
import type { NodeConfigSchemaDTO, NodeMetaDTO, SchemaFieldDTO, ValueType } from '@/types/contract'
import type { WorkflowNode } from '@/types/workflow'
import { debounce } from '@/utils/debounce'
import { getBusinessNodeType } from '@/adapters/workflow-graph'

function applyDefaults(config: Record<string, unknown>, schema?: NodeConfigSchemaDTO) {
  const nextConfig = { ...config }
  for (const section of schema?.sections ?? []) {
    for (const field of section.fields ?? []) {
      if (nextConfig[field.field] === undefined && field.defaultValue !== undefined) {
        nextConfig[field.field] = field.defaultValue
      }
    }
  }
  return nextConfig
}

function sanitizeDraftValue(value: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(value).filter(([key]) => !key.startsWith('__')))
}

export function usePanelController(nodeRef: MaybeRefOrGetter<WorkflowNode | undefined>) {
  const workflow = useWorkflowStore()
  const graphStore = useWorkflowGraphStore()
  const draft = reactive<Record<string, unknown>>({})
  const schema = ref<NodeConfigSchemaDTO>()
  const meta = ref<NodeMetaDTO>()
  const schemaLoading = ref(false)
  const schemaError = ref<string>()
  const currentStatus = ref<'draft' | 'error'>('draft')
  const { candidateSlots, loading: candidateLoading, loadCandidates } = useMappingCandidates(nodeRef, () => schema.value)
  const scheduleSync = debounce((nodeId: string, nextConfig: Record<string, unknown>, nextStatus: 'draft' | 'error') => {
    workflow.updateNodeConfig(nodeId, nextConfig, nextStatus)
  }, 300)
  let autoInferTimer: ReturnType<typeof setTimeout> | undefined

  function resetDraft(config: Record<string, unknown>, nextSchema?: NodeConfigSchemaDTO) {
    Object.keys(draft).forEach(key => delete draft[key])
    Object.assign(draft, applyDefaults(config, nextSchema), { __schema: nextSchema })
  }

  async function ensureMeta(node: WorkflowNode) {
    if (node.data.meta?.configSchema) {
      meta.value = node.data.meta
      schema.value = node.data.meta.configSchema
      schemaError.value = undefined
      return
    }
    schemaLoading.value = true
    schemaError.value = undefined
    try {
      meta.value = await getNodeDefinition(getBusinessNodeType(node))
      schema.value = meta.value.configSchema
    }
    catch (error) {
      schemaError.value = error instanceof Error ? error.message : '加载配置失败'
      meta.value = node.data.meta
      schema.value = node.data.meta?.configSchema
    }
    finally {
      schemaLoading.value = false
    }
  }

  async function tryAutoInferSchema(node: WorkflowNode) {
    if (getBusinessNodeType(node) !== 'sql_query') return
    if (!node.data.config.datasourceId || !node.data.config.sqlTemplate) return
    if (node.data.schema?.fields?.length) return
    try {
      const result = await inferQuerySchema(buildQueryRequest(node))
      if (result?.fields?.length) {
        workflow.updateNodeSchema(node.id, result)
        workflow.propagateSchemaFrom(node.id)
        await loadCandidates()
      }
    }
    catch {
    }
  }

  async function refresh() {
    const node = toValue(nodeRef)
    if (!node) {
      meta.value = undefined
      schema.value = undefined
      schemaError.value = undefined
      currentStatus.value = 'draft'
      resetDraft({}, undefined)
      return
    }
    await ensureMeta(node)
    currentStatus.value = node.data.status === 'error' ? 'error' : 'draft'
    let baseConfig: Record<string, unknown> = { ...(node.data.config ?? {}) }
    if (getBusinessNodeType(node) === 'data_join') {
      const { leftRef, rightRef, leftFields, rightFields } = resolveDataJoinInputs(node)
      if (leftRef) baseConfig = { ...baseConfig, leftDatasetRef: leftRef }
      if (rightRef) baseConfig = { ...baseConfig, rightDatasetRef: rightRef }
      baseConfig.__leftFields = leftFields
      baseConfig.__rightFields = rightFields
      const refsChanged = (leftRef && node.data.config?.leftDatasetRef !== leftRef)
        || (rightRef && node.data.config?.rightDatasetRef !== rightRef)
      if (refsChanged) {
        scheduleSync(node.id, sanitizeDraftValue(baseConfig), currentStatus.value)
      }
    }
    resetDraft(baseConfig, schema.value)
    await loadCandidates()
    if (autoInferTimer) clearTimeout(autoInferTimer)
    autoInferTimer = setTimeout(() => {
      tryAutoInferSchema(node).catch(() => undefined)
    }, 500)
  }

  function getNodeFieldNames(node: WorkflowNode): string[] {
    if (node.data.schema?.fields?.length) {
      return node.data.schema.fields.map(f => f.name ?? f.fieldId ?? '').filter(Boolean)
    }
    const dataset = node.data.debugResult?.result?.dataset
    if (dataset?.schema?.fields?.length) {
      return dataset.schema.fields.map((f: { name?: string, fieldId?: string }) => f.name ?? f.fieldId ?? '').filter(Boolean)
    }
    if (dataset?.rows?.length) return Object.keys(dataset.rows[0])
    return []
  }

  function resolveDataJoinInputs(node: WorkflowNode) {
    const edges = graphStore.edges.filter(e => e.target === node.id)
    const leftEdge = edges.find(e => e.targetHandle === 'leftDataset')
    const rightEdge = edges.find(e => e.targetHandle === 'rightDataset')
    const leftNode = leftEdge ? graphStore.getNodeById(leftEdge.source) : undefined
    const rightNode = rightEdge ? graphStore.getNodeById(rightEdge.source) : undefined
    return {
      leftRef: leftEdge?.source,
      rightRef: rightEdge?.source,
      leftFields: leftNode ? getNodeFieldNames(leftNode) : [],
      rightFields: rightNode ? getNodeFieldNames(rightNode) : [],
    }
  }

  function syncNode(nextConfig: Record<string, unknown>) {
    const node = toValue(nodeRef)
    if (!node) {
      return
    }
    scheduleSync(node.id, nextConfig, currentStatus.value)
  }

  function handleUpdate(value: Record<string, unknown>) {
    Object.keys(draft).forEach(key => delete draft[key])
    Object.assign(draft, value, { __schema: schema.value })
    syncNode(sanitizeDraftValue({ ...draft }))
    if (Array.isArray(value.outputs)) {
      syncOutputsToNodeSchema(value.outputs as Array<Record<string, unknown>>)
    }
  }

  function syncOutputsToNodeSchema(outputs: Array<Record<string, unknown>>) {
    const node = toValue(nodeRef)
    if (!node) return
    const fields: SchemaFieldDTO[] = outputs
      .filter(o => typeof o.name === 'string' && (o.name as string).trim())
      .map(o => ({
        fieldId: o.name as string,
        name: o.name as string,
        displayName: (typeof o.label === 'string' && o.label.trim()) ? o.label : o.name as string,
        valueType: (o.valueType as ValueType) ?? 'STRING',
        nullable: true,
      }))
    if (fields.length === 0) return
    const newSchema = {
      protocolVersion: '1',
      schemaId: `declared-${node.id}`,
      schemaVersion: '1',
      kind: 'DATASET',
      fields,
    }
    graphStore.updateNodeSchema(node.id, newSchema)
    graphStore.propagateSchemaFrom(node.id, false)
  }

  function handleValid(nextValid: boolean) {
    currentStatus.value = nextValid ? 'draft' : 'error'
    syncNode(sanitizeDraftValue({ ...draft }))
  }

  watch(() => toValue(nodeRef)?.id, async () => {
    await refresh()
  }, { immediate: true })

  watch(() => {
    const node = toValue(nodeRef)
    if (!node) return undefined
    const upstreams = graphStore.getUpstreamNodes(node.id)
    // 监听所有上游节点的 schemaId，任意一个变化都触发 candidates 刷新
    return upstreams.map(u => u.data.schema?.schemaId).join(',')
  }, () => {
    loadCandidates().catch(() => undefined)
  })

  // data_join 节点：监听连边变化，自动刷新 leftDatasetRef / rightDatasetRef
  watch(() => {
    const node = toValue(nodeRef)
    if (!node || getBusinessNodeType(node) !== 'data_join') return undefined
    return graphStore.edges
      .filter(e => e.target === node.id)
      .map(e => `${e.source}:${e.targetHandle ?? ''}`)
      .sort()
      .join(',')
  }, async () => {
    await refresh()
  })

  return {
    draft,
    meta: computed(() => meta.value),
    schema: computed(() => schema.value),
    schemaLoading: computed(() => schemaLoading.value),
    schemaError: computed(() => schemaError.value),
    candidateSlots,
    candidateLoading,
    refreshCandidates: loadCandidates,
    handleUpdate,
    handleValid,
  }
}
