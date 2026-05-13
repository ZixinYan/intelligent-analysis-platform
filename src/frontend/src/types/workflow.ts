export type AnalysisNodeStatus = 'idle' | 'draft' | 'valid' | 'running' | 'success' | 'error' | 'skipped'

export interface WorkflowNodeData {
  nodeType: string
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
  data: WorkflowNodeData
}

export interface WorkflowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string | null
  targetHandle?: string | null
  animated?: boolean
  condition?: 'true' | 'false' | null   // null = 无条件边
  conditionLabel?: string               // 画布标注文字
}
