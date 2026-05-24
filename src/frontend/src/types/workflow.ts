export type AnalysisNodeStatus = 'idle' | 'draft' | 'valid' | 'running' | 'success' | 'error' | 'skipped'

export interface WorkflowViewport {
  x: number
  y: number
  zoom: number
}

export interface WorkflowNodeData {
  type: string
  nodeType?: string
  title: string
  meta?: import('./contract').NodeMetaDTO
  config: Record<string, unknown>
  status: AnalysisNodeStatus
  preview?: string[]
  schema?: import('./contract').SchemaInferResultDTO
  selected?: boolean
  debugResult?: import('./contract').NodeResultDTO
  mockInputs?: Record<string, unknown>
}

export interface WorkflowNode {
  id: string
  type: string
  position: { x: number; y: number }
  selected?: boolean
  data: WorkflowNodeData
}

export interface WorkflowEdge {
  id: string
  type?: string
  source: string
  target: string
  sourceHandle?: string | null
  targetHandle?: string | null
  animated?: boolean
  selected?: boolean
  condition?: string | null
  conditionLabel?: string
}

export interface WorkflowGraph {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  viewport: WorkflowViewport
}
