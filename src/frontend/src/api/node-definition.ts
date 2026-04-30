import client, { unwrapResponse } from './client'
import type { FieldCandidateSlotDTO, NodeCapabilityDTO, NodeMetaDTO, SchemaInferResultDTO } from '@/types/contract'

export function listNodeDefinitions() {
  return unwrapResponse<NodeMetaDTO[]>(client.get('/api/v1/node-definitions'))
}

export function getNodeDefinition(nodeType: string) {
  return unwrapResponse<NodeMetaDTO>(client.get(`/api/v1/node-definitions/${nodeType}`))
}

export function inferNodeSchema(nodeType: string) {
  return unwrapResponse<SchemaInferResultDTO>(client.get(`/api/v1/node-definitions/${nodeType}/schema-infer`))
}

export function getMappingCandidates(nodeType: string, renderer: string) {
  return unwrapResponse<FieldCandidateSlotDTO[]>(
    client.get(`/api/v1/node-definitions/${nodeType}/mapping-candidates`, {
      params: { renderer },
    }),
  )
}

export function listComputeCapabilities() {
  return unwrapResponse<NodeCapabilityDTO[]>(client.get('/api/v1/node-definitions/compute-capabilities'))
}
