import client, { unwrapResponse } from './client'
import type { AsyncSubmitResponseDTO, QueryRequestDTO, QueryResultDTO, SchemaInferResultDTO, ValidateResultDTO } from '@/types/contract'

export function validateQuery(payload: QueryRequestDTO) {
  return unwrapResponse<ValidateResultDTO>(client.post('/api/v1/query/validate', payload))
}

export function previewQuery(payload: QueryRequestDTO) {
  return unwrapResponse<QueryResultDTO>(client.post('/api/v1/query/preview', payload))
}

export function runQueryAsync(payload: QueryRequestDTO) {
  return unwrapResponse<AsyncSubmitResponseDTO>(client.post('/api/v1/query/run-async', payload))
}

export function getQueryStatus(queryId: string) {
  return unwrapResponse<QueryResultDTO>(client.get(`/api/v1/query/${queryId}/status`))
}

export function cancelQuery(queryId: string) {
  return unwrapResponse<void>(client.delete(`/api/v1/query/${queryId}/cancel`))
}

export function inferQuerySchema(payload: QueryRequestDTO) {
  return unwrapResponse<SchemaInferResultDTO>(client.post('/api/v1/query/schema/infer', payload))
}
