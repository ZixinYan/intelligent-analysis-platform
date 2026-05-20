import { computed, ref, type MaybeRefOrGetter, toValue } from 'vue'
import { getMappingCandidates, getMappingCandidatesWithFields } from '@/api/node-definition'
import { getRawNodeType } from '@/adapters/workflow-graph'
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
      const nodeType = getRawNodeType(node)
      if (!nodeType) {
        candidateSlots.value = []
        return
      }
      const renderer = String(node.data.config.chartType ?? nodeType ?? 'default')

      let result: FieldCandidateSlotDTO[]
      try {
        result = await getMappingCandidatesWithFields(nodeType, {
          renderer,
          upstreamFields: upstream.data.schema.fields,
        })
      }
      catch {
        result = await getMappingCandidates(nodeType, renderer)
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
