import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { NodeDebugRequestDTO, NodeResultDTO } from '@/types/contract'
import type { AnalysisNodeStatus } from '@/types/workflow'
import { runNodeDebug as runNodeDebugApi } from '@/api/node-debug'
import { getBusinessNodeType } from '@/adapters/workflow-graph'
import { inferSchemaFromDataset, useWorkflowGraphStore } from './workflowGraph'
import { useWorkflowStream, type StreamNodeState } from '@/composables/useWorkflowStream'

export type { StreamNodeState }

export const useWorkflowDebugStore = defineStore('workflowDebug', () => {
  const debugActiveTab = ref<'config' | 'input' | 'output'>('config')
  const debugLoadingNodeId = ref<string>()

  // ── 整体工作流流式运行 ──────────────────────────────────────────
  const { nodeStates: workflowNodeStates, isStreaming, streamError, startStream, stopStream, flattenChunks } = useWorkflowStream()

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

  /**
   * 运行整个工作流（SSE 流式）。
   * 同时更新 workflowNodeStates（面板数据）和画布节点状态徽章。
   */
  async function runWorkflow(workflowId: string) {
    const graphStore = useWorkflowGraphStore()
    graphStore.graph.nodes.forEach(n => graphStore.updateNodeStatus(n.id, 'idle'))
    // 使用 startStream 驱动 workflowNodeStates，同时 watch 变化以同步画布状态
    const unwatch = watchNodeStatesToCanvas(graphStore)
    try {
      await startStream(workflowId, {})
    } finally {
      unwatch()
    }
  }

  /**
   * 监听 workflowNodeStates 变化，将节点状态同步写回画布 shell 徽章。
   * 返回取消监听函数。
   */
  function watchNodeStatesToCanvas(graphStore: ReturnType<typeof useWorkflowGraphStore>) {
    // 每 100ms 轮询一次（reactive Map 无法直接被 watch，用 requestAnimationFrame 代替）
    let stopped = false
    const prev = new Map<string, string>()

    function sync() {
      if (stopped) return
      workflowNodeStates.forEach((state, nodeId) => {
        const prev_status = prev.get(nodeId)
        if (prev_status !== state.status) {
          prev.set(nodeId, state.status)
          const canvasStatus: AnalysisNodeStatus =
            state.status === 'running' ? 'running'
            : state.status === 'success' ? 'success'
            : state.status === 'error' ? 'error'
            : state.status === 'skipped' ? 'idle'
            : 'idle'
          graphStore.updateNodeStatus(nodeId, canvasStatus)
        }
      })
      requestAnimationFrame(sync)
    }
    requestAnimationFrame(sync)
    return () => { stopped = true }
  }

  function stopWorkflow() {
    stopStream()
  }

  function resetDebug() {
    debugActiveTab.value = 'config'
    debugLoadingNodeId.value = undefined
    stopStream()
  }

  return {
    debugActiveTab,
    debugLoadingNodeId,
    workflowNodeStates,
    isStreaming,
    streamError,
    setDebugTab,
    setNodeMockInputs,
    runNodeDebug,
    runWorkflow,
    stopWorkflow,
    flattenChunks,
    resetDebug,
  }
})
