import client, { unwrapResponse } from './client'
import type {
  DatasourceCreateRequestDTO,
  DatasourceDTO,
  DatasourceQueryRequestDTO,
  DatasourceTestConnectionResultDTO,
  DatasourceUpdateRequestDTO,
  PageResult,
} from '@/types/contract'

export function listDatasources(params: DatasourceQueryRequestDTO = {}) {
  return unwrapResponse<PageResult<DatasourceDTO>>(client.get('/api/v1/datasources', { params }))
}

export function getDatasource(id: string) {
  return unwrapResponse<DatasourceDTO>(client.get(`/api/v1/datasources/${id}`))
}

export function createDatasource(payload: DatasourceCreateRequestDTO) {
  return unwrapResponse<DatasourceDTO>(client.post('/api/v1/datasources', payload))
}

export function updateDatasource(id: string, payload: DatasourceUpdateRequestDTO) {
  return unwrapResponse<DatasourceDTO>(client.put(`/api/v1/datasources/${id}`, payload))
}

export function removeDatasource(id: string) {
  return unwrapResponse<void>(client.delete(`/api/v1/datasources/${id}`))
}

export function testDatasourceConnection(id: string) {
  return unwrapResponse<DatasourceTestConnectionResultDTO>(client.post(`/api/v1/datasources/${id}/test-connection`))
}

export function getDatasourceTables(id: string) {
  return unwrapResponse<string[]>(client.get(`/api/v1/datasources/${id}/tables`))
}
