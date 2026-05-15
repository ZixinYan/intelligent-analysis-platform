import client, { unwrapResponse } from './client'
import type { ActiveQueryDTO, OpsMetricsSummaryDTO, SlowQueryDTO } from '@/types/contract'

export function getOpsSummary() {
  return unwrapResponse<OpsMetricsSummaryDTO>(client.get('/api/v1/ops/metrics/summary'))
}

export function getSlowQueries(params: { limit?: number; since?: number } = {}) {
  return unwrapResponse<SlowQueryDTO[]>(client.get('/api/v1/ops/slow-queries', { params }))
}

export function getActiveQueries() {
  return unwrapResponse<ActiveQueryDTO[]>(client.get('/api/v1/ops/active-queries'))
}
