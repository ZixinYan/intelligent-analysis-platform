export type AnalysisNodeStatus = 'idle' | 'draft' | 'valid' | 'running' | 'success' | 'error'

export interface WorkflowNodeData {
  nodeType: string
  title: string
  meta?: import('./contract').NodeMetaDTO
  config: Record<string, unknown>
  status: AnalysisNodeStatus
  preview?: string[]
  schema?: import('./contract').SchemaInferResultDTO
  selected?: boolean
}

export interface WorkflowNode {
  id: string
  type: string
  position: { x: number; y: number }
  data: WorkflowNodeData
}

export interface WorkflowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string | null
  targetHandle?: string | null
  animated?: boolean
}
