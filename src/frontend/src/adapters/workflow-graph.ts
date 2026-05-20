import type {
  WorkflowDefinitionDTO,
  WorkflowEdgeDTO,
  WorkflowPositionDTO,
  WorkflowSaveRequestDTO,
} from '@/types/contract'
import type { AnalysisNodeStatus, WorkflowEdge, WorkflowGraph, WorkflowNode, WorkflowViewport } from '@/types/workflow'
import { normalizeNodeType, toRawNodeType } from '@/constants/analysis-nodes'
import { buildNodePreview } from '@/utils/node-preview'

const DEFAULT_NODE_POSITION = { x: 120, y: 120 }
export const DEFAULT_VIEWPORT: WorkflowViewport = { x: 0, y: 0, zoom: 1 }
export const WORKFLOW_RENDERER_NODE_TYPE = 'workflow-node'
export const WORKFLOW_INSERT_EDGE_TYPE = 'workflow-insert-edge'

export function getRawNodeType(node: Pick<WorkflowNode, 'data'> | WorkflowNode['data']) {
  const data = 'data' in node ? node.data : node
  return toRawNodeType(data.meta?.nodeType || data.nodeType || data.type || '')
}

export function getBusinessNodeType(node: Pick<WorkflowNode, 'data'> | WorkflowNode['data']) {
  const rawType = getRawNodeType(node)
  return rawType ? normalizeNodeType(rawType) : ''
}

export function createEmptyWorkflowGraph(): WorkflowGraph {
  return {
    nodes: [],
    edges: [],
    viewport: { ...DEFAULT_VIEWPORT },
  }
}

function toWorkflowNode(definition: WorkflowDefinitionDTO, node: WorkflowDefinitionDTO['nodes'][number]): WorkflowNode {
  const meta = node.metadata
  const config = (node.config ?? {}) as Record<string, unknown>
  const position = definition.positions?.[node.nodeId] ?? DEFAULT_NODE_POSITION
  const rawType = toRawNodeType(node.nodeType || meta?.nodeType || '')
  const businessType = normalizeNodeType(rawType)

  return {
    id: node.nodeId,
    type: WORKFLOW_RENDERER_NODE_TYPE,
    position: { x: position.x ?? DEFAULT_NODE_POSITION.x, y: position.y ?? DEFAULT_NODE_POSITION.y },
    data: {
      type: rawType,
      nodeType: rawType,
      title: meta?.displayName ?? (businessType || rawType),
      meta,
      config,
      status: 'idle' as AnalysisNodeStatus,
      preview: buildNodePreview(meta, config, businessType),
    },
  }
}

export function definitionDtoToGraph(definition: WorkflowDefinitionDTO, viewport: WorkflowViewport = DEFAULT_VIEWPORT): WorkflowGraph {
  return {
    nodes: (definition.nodes ?? []).map(node => toWorkflowNode(definition, node)),
    edges: (definition.edges ?? []).map(edge => ({
      id: edge.id,
      type: WORKFLOW_INSERT_EDGE_TYPE,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle ?? undefined,
      targetHandle: edge.targetHandle ?? undefined,
      animated: false,
      condition: edge.condition ?? undefined,
      conditionLabel: edge.condition ?? undefined,
    } satisfies WorkflowEdge)),
    viewport: { ...viewport },
  }
}

function toPositionMap(nodes: WorkflowNode[]) {
  return Object.fromEntries(nodes.map(node => [node.id, { x: node.position.x, y: node.position.y } satisfies WorkflowPositionDTO]))
}

export function graphToSaveRequest(graph: WorkflowGraph, workflowName: string): WorkflowSaveRequestDTO {
  return {
    workflowName: workflowName.trim() || '未命名工作流',
    nodes: graph.nodes.map(node => ({
      nodeId: node.id,
      nodeType: getRawNodeType(node),
      category: node.data.meta?.category,
      version: node.data.meta?.nodeVersion,
      metadata: node.data.meta,
      config: node.data.config,
    })),
    edges: graph.edges.map(edge => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle ?? undefined,
      targetHandle: edge.targetHandle ?? undefined,
      condition: edge.condition ?? undefined,
    } satisfies WorkflowEdgeDTO)),
    positions: toPositionMap(graph.nodes),
  }
}
