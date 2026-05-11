import client, { unwrapResponse } from './client'
import type { NodeDebugRequestDTO, NodeResultDTO } from '@/types/contract'

export function runNodeDebug(payload: NodeDebugRequestDTO) {
  return unwrapResponse<NodeResultDTO>(client.post('/api/v1/node-debug', payload))
}
