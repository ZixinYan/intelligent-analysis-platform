import { computed, reactive, ref, watch, type MaybeRefOrGetter, toValue } from 'vue'
import { getNodeDefinition } from '@/api/node-definition'
import { buildQueryRequest } from '@/composables/useNodeDebug'
import { inferQuerySchema } from '@/api/query'
import { useMappingCandidates } from '@/composables/useMappingCandidates'
import { useWorkflowStore } from '@/stores/workflow'
import type { NodeConfigSchemaDTO, NodeMetaDTO } from '@/types/contract'
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
  return Object.fromEntries(Object.entries(value).filter(([key]) => key !== '__schema'))
}

export function usePanelController(nodeRef: MaybeRefOrGetter<WorkflowNode | undefined>) {
  const workflow = useWorkflowStore()
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
    resetDraft(node.data.config ?? {}, schema.value)
    await loadCandidates()
    if (autoInferTimer) clearTimeout(autoInferTimer)
    autoInferTimer = setTimeout(() => {
      tryAutoInferSchema(node).catch(() => undefined)
    }, 500)
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
    const upstream = workflow.getUpstreamNode(node.id)
    return upstream?.data.schema?.schemaId
  }, () => {
    loadCandidates().catch(() => undefined)
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
