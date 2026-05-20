import client, { requestContextHeaders, unwrapResponse } from './client'
import type {
  AsyncSubmitResponseDTO,
  CreateTriggerRequestDTO,
  PageResult,
  TriggerDTO,
  TriggerStatus,
  WorkflowDefinitionDTO,
  WorkflowQueryRequestDTO,
  WorkflowRunRequestDTO,
  WorkflowSaveRequestDTO,
  WorkflowStreamEventDTO,
  WorkflowVersionDTO,
  WorkflowVersionDiffDTO,
} from '@/types/contract'

export function listWorkflows(params: WorkflowQueryRequestDTO = {}) {
  return unwrapResponse<PageResult<WorkflowDefinitionDTO>>(client.get('/api/v1/workflows', { params }))
}

export function getWorkflow(id: string) {
  return unwrapResponse<WorkflowDefinitionDTO>(client.get(`/api/v1/workflows/${id}`))
}

export function createWorkflow(payload: WorkflowSaveRequestDTO) {
  return unwrapResponse<WorkflowDefinitionDTO>(client.post('/api/v1/workflows', payload))
}

export function updateWorkflow(id: string, payload: WorkflowSaveRequestDTO) {
  return unwrapResponse<WorkflowDefinitionDTO>(client.put(`/api/v1/workflows/${id}`, payload))
}

export async function* runWorkflowStream(
  workflowId: string,
  request: WorkflowRunRequestDTO,
  signal?: AbortSignal,
): AsyncIterable<WorkflowStreamEventDTO> {
  const response = await fetch(`/api/v1/workflow-stream/${workflowId}/run`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...requestContextHeaders,
    },
    body: JSON.stringify(request),
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`stream request failed: ${response.status} ${response.statusText}`)
  }

  yield* parseSseStream<WorkflowStreamEventDTO>(response.body)
}

async function* parseSseStream<T>(body: ReadableStream<Uint8Array>): AsyncIterable<T> {
  const decoder = new TextDecoder()
  const reader = body.getReader()
  let buffer = ''

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() ?? ''

      for (const block of blocks) {
        const dataLine = block
          .split('\n')
          .find(line => line.startsWith('data:'))
        if (!dataLine) continue
        const json = dataLine.slice('data:'.length).trim()
        if (json) {
          try {
            yield JSON.parse(json) as T
          }
          catch {
          }
        }
      }
    }
  }
  finally {
    reader.releaseLock()
  }
}

export function listVersions(workflowId: string, page = 1, pageSize = 20) {
  return unwrapResponse<PageResult<WorkflowVersionDTO>>(
    client.get(`/api/v1/workflows/${workflowId}/versions`, { params: { page, pageSize } }),
  )
}

export function snapshotVersion(workflowId: string, changeSummary?: string) {
  return unwrapResponse<WorkflowVersionDTO>(
    client.post(`/api/v1/workflows/${workflowId}/versions`, { changeSummary }),
  )
}

export function getVersion(workflowId: string, versionNumber: number) {
  return unwrapResponse<WorkflowDefinitionDTO>(
    client.get(`/api/v1/workflows/${workflowId}/versions/${versionNumber}`),
  )
}

export function publishVersion(workflowId: string, versionNumber: number) {
  return unwrapResponse<void>(
    client.post(`/api/v1/workflows/${workflowId}/versions/${versionNumber}/publish`),
  )
}

export function rollbackVersion(workflowId: string, versionNumber: number) {
  return unwrapResponse<WorkflowVersionDTO>(
    client.post(`/api/v1/workflows/${workflowId}/versions/${versionNumber}/rollback`),
  )
}

export function diffVersions(workflowId: string, from: number, to: number) {
  return unwrapResponse<WorkflowVersionDiffDTO>(
    client.get(`/api/v1/workflows/${workflowId}/versions/diff`, { params: { from, to } }),
  )
}

export function createTrigger(workflowId: string, payload: CreateTriggerRequestDTO) {
  return unwrapResponse<TriggerDTO>(
    client.post(`/api/v1/workflows/${workflowId}/triggers`, payload),
  )
}

export function listTriggers(workflowId: string) {
  return unwrapResponse<TriggerDTO[]>(
    client.get(`/api/v1/workflows/${workflowId}/triggers`),
  )
}

export function updateTriggerStatus(triggerId: string, status: TriggerStatus) {
  return unwrapResponse<TriggerDTO>(
    client.patch(`/api/v1/triggers/${triggerId}/status`, { status }),
  )
}

export function deleteTrigger(triggerId: string) {
  return unwrapResponse<void>(
    client.delete(`/api/v1/triggers/${triggerId}`),
  )
}

export function fireTrigger(triggerId: string) {
  return unwrapResponse<AsyncSubmitResponseDTO>(
    client.post(`/api/v1/triggers/${triggerId}/fire`),
  )
}
