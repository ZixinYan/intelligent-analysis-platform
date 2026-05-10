import { computed, ref, type MaybeRefOrGetter, toValue } from 'vue'
import { getMappingCandidates, getMappingCandidatesWithFields } from '@/api/node-definition'
import { useWorkflowStore } from '@/stores/workflow'
import type { FieldCandidateSlotDTO, NodeConfigSchemaDTO } from '@/types/contract'
import type { WorkflowNode } from '@/types/workflow'

function needsMappingCandidates(schema?: NodeConfigSchemaDTO) {
  return schema?.sections
    ?.flatMap(section => section.fields)
    .some(field => field.componentType === 'FIELD_PICKER' || field.componentType === 'FIELD_MULTI_SELECTOR') ?? false
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
    const schema = toValue(schemaRef)
    if (!node || !needsMappingCandidates(schema ?? node.data.meta?.configSchema)) {
      candidateSlots.value = []
      return
    }
    const upstream = workflow.getUpstreamNode(node.id)
    if (!upstream?.data.schema?.fields?.length) {
      candidateSlots.value = []
      return
    }
    loading.value = true
    const currentRequestId = ++requestId
    try {
      const renderer = String(node.data.config.chartType ?? node.data.nodeType ?? 'default')

      // Try POST with upstream fields first, fall back to GET
      let result: FieldCandidateSlotDTO[]
      try {
        result = await getMappingCandidatesWithFields(node.data.nodeType, {
          renderer,
          upstreamFields: upstream.data.schema.fields,
        })
      }
      catch {
        // Fallback: GET without upstream fields
        result = await getMappingCandidates(node.data.nodeType, renderer)
      }
      if (currentRequestId === requestId) {
        candidateSlots.value = result
      }
    }
    finally {
      if (currentRequestId === requestId) {
        loading.value = false
      }
    }
  }

  return {
    candidateSlots: computed(() => candidateSlots.value),
    loading: computed(() => loading.value),
    loadCandidates,
  }
}
