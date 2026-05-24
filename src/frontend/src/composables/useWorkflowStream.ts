import { reactive, ref } from 'vue'
import { runWorkflowStream } from '@/api/workflow'
import type {
  ErrorInfoDTO,
  NodeResultEventDTO,
  StandardResultDTO,
  WorkflowRunRequestDTO,
} from '@/types/contract'

/**
 * 单个节点的实时流式状态，在 SSE 事件驱动下持续更新。
 *
 * chunks 字段用于大数据集场景：后端将结果分批推送（node_progress 事件），
 * 每批对应 chunks[chunkIndex]，全部推送完成后通过 flattenChunks() 合并。
 */
export interface StreamNodeState {
  /** 节点执行状态 */
  status: 'pending' | 'running' | 'success' | 'error' | 'skipped'
  /** 最终结果（stream 结束后携带完整摘要） */
  result?: StandardResultDTO
  /** 分块数据（大数据集：每个索引对应一个 node_progress chunk） */
  chunks: Record<string, unknown>[][]
  elapsedMs?: number
  error?: ErrorInfoDTO
}

/**
 * 工作流流式执行 composable。
 *
 * 用法：
 * ```ts
 * const { nodeStates, isStreaming, startStream, stopStream } = useWorkflowStream()
 * await startStream(workflowId, request)
 * ```
 *
 * - `node_start`    → nodeStates[nodeId].status = 'running'
 * - `node_progress` → nodeStates[nodeId].chunks[chunkIndex] = rows
 * - `node_result`   → nodeStates[nodeId].status = 'success' | 'error'
 * - `workflow_done` | `workflow_error` → isStreaming = false
 */
export function useWorkflowStream() {
  const nodeStates = reactive<Map<string, StreamNodeState>>(new Map())
  const isStreaming = ref(false)
  const streamError = ref<ErrorInfoDTO>()

  let abortController: AbortController | null = null

  function getOrCreate(nodeId: string): StreamNodeState {
    if (!nodeStates.has(nodeId)) {
      nodeStates.set(nodeId, { status: 'pending', chunks: [] })
    }
    return nodeStates.get(nodeId)!
  }

  /** 启动流式执行。会自动停止上一次未完成的 stream。 */
  async function startStream(workflowId: string, request: WorkflowRunRequestDTO) {
    stopStream()
    nodeStates.clear()
    streamError.value = undefined
    isStreaming.value = true

    abortController = new AbortController()
    try {
      for await (const event of runWorkflowStream(workflowId, request, abortController.signal)) {
        switch (event.eventType) {
          case 'node_start': {
            if (!event.nodeId) {
              break
            }
            const state = getOrCreate(event.nodeId)
            state.status = 'running'
            state.chunks = []
            break
          }
          case 'node_progress': {
            if (!event.nodeId || event.chunkIndex === undefined) {
              break
            }
            const state = getOrCreate(event.nodeId)
            state.chunks[event.chunkIndex] = event.rows ?? []
            break
          }
          case 'node_result': {
            const payload = event as NodeResultEventDTO
            const state = getOrCreate(payload.nodeId)
            state.status = payload.status === 'SUCCEEDED' ? 'success' : 'error'
            state.result = payload.result
            state.elapsedMs = payload.meta?.elapsedMs ?? undefined
            break
          }
          case 'workflow_done': {
            isStreaming.value = false
            break
          }
          case 'workflow_error': {
            streamError.value = event.error
            isStreaming.value = false
            break
          }
        }
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        // user called stopStream() — normal cancellation
      } else {
        streamError.value = {
          message: err instanceof Error ? err.message : '流式请求失败',
        }
      }
      isStreaming.value = false
    } finally {
      abortController = null
    }
  }

  /** 中止当前流式执行 */
  function stopStream() {
    abortController?.abort()
    abortController = null
    isStreaming.value = false
  }

  /**
   * 将分块数据合并成完整的行数组（大数据集场景）。
   * 若节点结果行已在 result.dataset.rows 中，直接返回。
   */
  function flattenChunks(nodeId: string): Record<string, unknown>[] {
    const state = nodeStates.get(nodeId)
    if (!state) return []
    if (state.result?.dataset?.rows?.length) {
      return state.result.dataset.rows as Record<string, unknown>[]
    }
    return state.chunks.flat()
  }

  return {
    nodeStates,
    isStreaming,
    streamError,
    startStream,
    stopStream,
    flattenChunks,
  }
}
