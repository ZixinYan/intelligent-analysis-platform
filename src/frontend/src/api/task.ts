import client, { unwrapResponse } from './client'
import type { AsyncTaskStatusDTO } from '@/types/contract'

export function getTaskStatus(taskId: string) {
  return unwrapResponse<AsyncTaskStatusDTO>(client.get(`/api/v1/tasks/${taskId}`))
}

export function cancelTask(taskId: string) {
  return unwrapResponse<void>(client.post(`/api/v1/tasks/${taskId}/cancel`))
}
