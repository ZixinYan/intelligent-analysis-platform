import client, { unwrapResponse } from './client'
import type { AsyncTaskStatusDTO } from '@/types/contract'

export function getTaskStatus(taskId: string) {
  return unwrapResponse<AsyncTaskStatusDTO>(client.get(`/api/v1/tasks/${taskId}`))
}

export function getAgentTaskStatus(taskId: string) {
  return getTaskStatus(taskId)
}

export function cancelTask(taskId: string) {
  return unwrapResponse<void>(client.post(`/api/v1/tasks/${taskId}/cancel`))
}

export function cancelAgentTask(taskId: string) {
  return cancelTask(taskId)
}

/**
 * 轮询异步任务直到终态（SUCCEEDED / FAILED / CANCELLED）或超时。
 */
export function pollTask(
  taskId: string,
  options: {
    intervalMs?: number
    timeoutMs?: number
    onProgress?: (status: AsyncTaskStatusDTO) => void
  } = {},
): Promise<AsyncTaskStatusDTO> {
  const { intervalMs = 2000, timeoutMs = 120_000, onProgress } = options
  const deadline = Date.now() + timeoutMs

  return new Promise((resolve, reject) => {
    const tick = async () => {
      if (Date.now() > deadline)
        return reject(new Error('Task polling timeout'))

      try {
        const status = await getTaskStatus(taskId)
        onProgress?.(status)

        if (status.status === 'SUCCEEDED' || status.status === 'FAILED' || status.status === 'CANCELLED')
          return resolve(status)

        setTimeout(tick, intervalMs)
      }
      catch (error) {
        reject(error)
      }
    }
    tick()
  })
}
