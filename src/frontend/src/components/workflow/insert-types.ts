import type { NodeMetaDTO } from '@/types/contract'

export interface InsertAnchor {
  x: number
  y: number
}

export type WorkflowInsertTrigger =
  | {
    kind: 'node-output'
    nodeId: string
    sourceHandle: string
    anchor: InsertAnchor
  }
  | {
    kind: 'node-input'
    nodeId: string
    targetHandle: string
    anchor: InsertAnchor
  }
  | {
    kind: 'edge'
    edgeId: string
    anchor: InsertAnchor
  }

export interface NodeCatalogGroup {
  category: string
  meta: {
    label: string
    color: string
    desc: string
  }
  nodes: NodeMetaDTO[]
}
