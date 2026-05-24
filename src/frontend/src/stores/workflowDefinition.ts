import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { PageResult, WorkflowDefinitionDTO } from '@/types/contract'
import { createWorkflow, getWorkflow, listWorkflows, updateWorkflow } from '@/api/workflow'
import { useWorkflowGraphStore } from './workflowGraph'
import { useWorkflowDebugStore } from './workflowDebug'

export const useWorkflowDefinitionStore = defineStore('workflowDefinition', () => {
  const workflowId = ref<string>()
  const workflowName = ref('未命名工作流')
  const saving = ref(false)
  const loading = ref(false)
  const workflowList = ref<WorkflowDefinitionDTO[]>([])

  async function loadList() {
    const result: PageResult<WorkflowDefinitionDTO> = await listWorkflows()
    workflowList.value = result.items
  }

  async function load(id: string) {
    if (!id || loading.value) return
    loading.value = true
    try {
      const graphStore = useWorkflowGraphStore()
      const definition = await getWorkflow(id)
      workflowId.value = definition.workflowId
      workflowName.value = definition.workflowName || '未命名工作流'
      graphStore.hydrateGraph(definition)
      await loadList()
    }
    finally {
      loading.value = false
    }
  }

  async function save() {
    if (saving.value) return
    saving.value = true
    try {
      const graphStore = useWorkflowGraphStore()
      const payload = graphStore.serialize(workflowName.value)
      const definition = workflowId.value
        ? await updateWorkflow(workflowId.value, payload)
        : await createWorkflow(payload)
      workflowId.value = definition.workflowId
      workflowName.value = definition.workflowName || '未命名工作流'
      graphStore.hydrateGraph(definition)
      await loadList()
    }
    finally {
      saving.value = false
    }
  }

  function reset() {
    workflowId.value = undefined
    workflowName.value = '未命名工作流'
    useWorkflowGraphStore().resetGraph()
    useWorkflowDebugStore().resetDebug()
  }

  return {
    workflowId,
    workflowName,
    saving,
    loading,
    workflowList,
    loadList,
    load,
    save,
    reset,
  }
})
