import client, { unwrapResponse } from './client'
import type {
  PageResult,
  SavedDatasetDetailDTO,
  SavedDatasetSummaryDTO,
} from '@/types/contract'

export function listDatasets(params: {
  page?: number
  pageSize?: number
  name?: string
  sourceWorkflowId?: string
} = {}) {
  return unwrapResponse<PageResult<SavedDatasetSummaryDTO>>(
    client.get('/api/v1/datasets', { params }),
  )
}

export function getDataset(datasetId: string, params: { page?: number; pageSize?: number } = {}) {
  return unwrapResponse<SavedDatasetDetailDTO>(
    client.get(`/api/v1/datasets/${datasetId}`, { params }),
  )
}

export function deleteDataset(datasetId: string) {
  return unwrapResponse<void>(client.delete(`/api/v1/datasets/${datasetId}`))
}

export function saveDatasetFromQuery(payload: {
  name: string
  description?: string
  queryId: string
}) {
  return unwrapResponse<SavedDatasetSummaryDTO>(
    client.post('/api/v1/datasets/from-query', payload),
  )
}

export function updateDatasetMeta(datasetId: string, payload: { name?: string; description?: string }) {
  return unwrapResponse<SavedDatasetSummaryDTO>(
    client.patch(`/api/v1/datasets/${datasetId}`, payload),
  )
}
