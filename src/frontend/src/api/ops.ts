import client, { unwrapResponse } from './client'
import type { OpsMetricsSummaryDTO } from '@/types/contract'

export function getOpsSummary() {
  return unwrapResponse<OpsMetricsSummaryDTO>(client.get('/api/v1/ops/metrics/summary'))
}
