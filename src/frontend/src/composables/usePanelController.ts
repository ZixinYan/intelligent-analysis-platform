import { computed, reactive, ref, watch, type MaybeRefOrGetter, toValue } from 'vue'
import { getNodeDefinition } from '@/api/node-definition'
import { useMappingCandidates } from '@/composables/useMappingCandidates'
import { useWorkflowStore } from '@/stores/workflow'
import type { NodeConfigSchemaDTO, NodeMetaDTO } from '@/types/contract'
import type { WorkflowNode } from '@/types/workflow'
import { debounce } from '@/utils/debounce'

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
      meta.value = await getNodeDefinition(node.data.nodeType)
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

  const activeMeta = computed(() => meta.value)
  const activeSchema = computed(() => schema.value)

  return {
    draft,
    meta: activeMeta,
    schema: activeSchema,
    schemaLoading: computed(() => schemaLoading.value),
    schemaError: computed(() => schemaError.value),
    candidateSlots,
    candidateLoading,
    refreshCandidates: loadCandidates,
    handleUpdate,
    handleValid,
  }
}
