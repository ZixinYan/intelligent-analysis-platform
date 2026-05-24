import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { NodeDebugRequestDTO, NodeResultDTO } from '@/types/contract'
import type { AnalysisNodeStatus } from '@/types/workflow'
import { runNodeDebug as runNodeDebugApi } from '@/api/node-debug'
import { getBusinessNodeType } from '@/adapters/workflow-graph'
import { inferSchemaFromDataset, useWorkflowGraphStore } from './workflowGraph'

export const useWorkflowDebugStore = defineStore('workflowDebug', () => {
  const debugActiveTab = ref<'config' | 'input' | 'output'>('config')
  const debugLoadingNodeId = ref<string>()

  function setDebugTab(tab: 'config' | 'input' | 'output') {
    debugActiveTab.value = tab
  }

  function setNodeMockInputs(nodeId: string, mockInputs: Record<string, unknown>) {
    const graphStore = useWorkflowGraphStore()
    graphStore.updateNode(nodeId, node => ({ ...node, data: { ...node.data, mockInputs } }))
  }

  async function runNodeDebug(nodeId: string) {
    const graphStore = useWorkflowGraphStore()
    const node = graphStore.graph.nodes.find(n => n.id === nodeId)
    if (!node) return
    debugActiveTab.value = 'output'
    graphStore.setSingleNodeSelection(nodeId)
    debugLoadingNodeId.value = nodeId
    graphStore.updateNodeStatus(nodeId, 'running')
    try {
      const payload: NodeDebugRequestDTO = {
        nodeId,
        node: {
          nodeId,
          nodeType: getBusinessNodeType(node),
          config: node.data.config,
        },
        upstreamMockInputs: graphStore.buildUpstreamInputs(nodeId),
      }
      const result: NodeResultDTO = await runNodeDebugApi(payload)
      graphStore.updateNode(nodeId, n => ({ ...n, data: { ...n.data, debugResult: result } }))
      const nextStatus: AnalysisNodeStatus = result.status === 'SUCCEEDED' ? 'success'
        : result.status === 'FAILED' ? 'error' : 'running'
      graphStore.updateNodeStatus(nodeId, nextStatus)
      if (result.status === 'SUCCEEDED') {
        const dataset = result.result?.dataset
        if (dataset) {
          const schema = inferSchemaFromDataset(dataset, nodeId)
          if (schema) {
            graphStore.updateNodeSchema(nodeId, schema)
            graphStore.propagateSchemaFrom(nodeId, true)
          }
        }
      }
    }
    catch (err) {
      const errorResult: NodeResultDTO = {
        nodeId,
        nodeType: getBusinessNodeType(node),
        status: 'FAILED',
        error: { message: err instanceof Error ? err.message : '节点执行失败' },
      }
      graphStore.updateNode(nodeId, n => ({ ...n, data: { ...n.data, debugResult: errorResult } }))
      graphStore.updateNodeStatus(nodeId, 'error')
    }
    finally {
      debugLoadingNodeId.value = undefined
    }
  }

  function resetDebug() {
    debugActiveTab.value = 'config'
    debugLoadingNodeId.value = undefined
  }

  return {
    debugActiveTab,
    debugLoadingNodeId,
    setDebugTab,
    setNodeMockInputs,
    runNodeDebug,
    resetDebug,
  }
})
