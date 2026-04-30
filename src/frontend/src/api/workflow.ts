import client, { unwrapResponse } from './client'
import type {
  PageResult,
  WorkflowDefinitionDTO,
  WorkflowQueryRequestDTO,
  WorkflowSaveRequestDTO,
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
