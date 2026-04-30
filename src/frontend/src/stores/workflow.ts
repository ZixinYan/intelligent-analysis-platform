import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Connection, NodeChange, XYPosition } from '@vue-flow/core'
import type {
  NodeMetaDTO,
  PageResult,
  WorkflowDefinitionDTO,
  WorkflowEdgeDTO,
  WorkflowPositionDTO,
  WorkflowSaveRequestDTO,
} from '@/types/contract'
import type { AnalysisNodeStatus, WorkflowEdge, WorkflowNode } from '@/types/workflow'
import { buildNodePreview, createDefaultNodeConfig } from '@/utils/node-preview'
import { createWorkflow, getWorkflow, listWorkflows, updateWorkflow } from '@/api/workflow'

function createNodeId(nodeType: string) {
  return `${nodeType}-${Math.random().toString(36).slice(2, 8)}`
}

function toPositionMap(nodes: WorkflowNode[]) {
  return Object.fromEntries(nodes.map(node => [node.id, { x: node.position.x, y: node.position.y } satisfies WorkflowPositionDTO]))
}

export const useWorkflowStore = defineStore('workflow', () => {
  const nodes = ref<WorkflowNode[]>([])
  const edges = ref<WorkflowEdge[]>([])
  const selectedNodeId = ref<string>()
  const workflowId = ref<string>()
  const workflowName = ref('未命名工作流')
  const saving = ref(false)
  const loading = ref(false)
  const workflowList = ref<WorkflowDefinitionDTO[]>([])

  const selectedNode = computed(() => nodes.value.find(node => node.id === selectedNodeId.value))

  function addNode(meta: NodeMetaDTO, position: XYPosition = { x: 120, y: 120 }) {
    const id = createNodeId(meta.nodeType)
    const config = createDefaultNodeConfig(meta)
    const node: WorkflowNode = {
      id,
      type: 'analysis-node',
      position,
      data: {
        nodeType: meta.nodeType,
        title: meta.displayName,
        meta,
        config,
        status: 'idle',
        preview: buildNodePreview(meta, config),
      },
    }
    nodes.value = [...nodes.value, node]
    selectedNodeId.value = id
  }

  function updateNodeConfig(nodeId: string, config: Record<string, unknown>, status: AnalysisNodeStatus = 'draft') {
    nodes.value = nodes.value.map((node) => {
      if (node.id !== nodeId) {
        return node
      }
      return {
        ...node,
        data: {
          ...node.data,
          config,
          status,
          preview: buildNodePreview(node.data.meta, config),
        },
      }
    })
  }

  function updateNodeStatus(nodeId: string, status: AnalysisNodeStatus, preview?: string[]) {
    nodes.value = nodes.value.map((node) => {
      if (node.id !== nodeId) {
        return node
      }
      return {
        ...node,
        data: {
          ...node.data,
          status,
          preview: preview ?? node.data.preview,
        },
      }
    })
  }

  function updateNodeSchema(nodeId: string, schema: WorkflowNode['data']['schema']) {
    nodes.value = nodes.value.map((node) => {
      if (node.id !== nodeId) {
        return node
      }
      return {
        ...node,
        data: {
          ...node.data,
          schema,
        },
      }
    })
  }

  function onNodesChange(changes: NodeChange[]) {
    for (const change of changes) {
      if (change.type === 'position' && change.position) {
        nodes.value = nodes.value.map(node => node.id === change.id ? { ...node, position: change.position } : node)
      }
      if (change.type === 'remove') {
        nodes.value = nodes.value.filter(node => node.id !== change.id)
        edges.value = edges.value.filter(edge => edge.source !== change.id && edge.target !== change.id)
      }
      if (change.type === 'select' && change.selected) {
        selectedNodeId.value = change.id
      }
    }
  }

  function onConnect(connection: Connection) {
    if (!connection.source || !connection.target) {
      return
    }
    const edge: WorkflowEdge = {
      id: `${connection.source}-${connection.target}-${Date.now()}`,
      source: connection.source,
      target: connection.target,
      sourceHandle: connection.sourceHandle,
      targetHandle: connection.targetHandle,
      animated: false,
    }
    edges.value = [...edges.value, edge]
  }

  function onNodeClick(payload: { node: WorkflowNode }) {
    selectedNodeId.value = payload.node.id
  }

  function getUpstreamNode(nodeId: string) {
    const edge = edges.value.find(item => item.target === nodeId)
    if (!edge) {
      return undefined
    }
    return nodes.value.find(item => item.id === edge.source)
  }

  function serialize(): WorkflowSaveRequestDTO {
    return {
      workflowName: workflowName.value.trim() || '未命名工作流',
      nodes: nodes.value.map(node => ({
        nodeId: node.id,
        nodeType: node.data.nodeType,
        category: node.data.meta?.category,
        version: node.data.meta?.nodeVersion,
        metadata: node.data.meta,
        config: node.data.config,
      })),
      edges: edges.value.map(edge => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        sourceHandle: edge.sourceHandle ?? undefined,
        targetHandle: edge.targetHandle ?? undefined,
      } satisfies WorkflowEdgeDTO)),
      positions: toPositionMap(nodes.value),
    }
  }

  function hydrate(definition: WorkflowDefinitionDTO) {
    workflowId.value = definition.workflowId
    workflowName.value = definition.workflowName || '未命名工作流'
    nodes.value = (definition.nodes ?? []).map((node) => {
      const meta = node.metadata
      const config = (node.config ?? {}) as Record<string, unknown>
      const position = definition.positions?.[node.nodeId] ?? { x: 120, y: 120 }
      return {
        id: node.nodeId,
        type: 'analysis-node',
        position: { x: position.x ?? 120, y: position.y ?? 120 },
        data: {
          nodeType: node.nodeType,
          title: meta?.displayName ?? node.nodeType,
          meta,
          config,
          status: 'idle',
          preview: buildNodePreview(meta, config),
        },
      }
    })
    edges.value = (definition.edges ?? []).map(edge => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle ?? undefined,
      targetHandle: edge.targetHandle ?? undefined,
      animated: false,
    }))
    selectedNodeId.value = undefined
  }

  async function save() {
    if (saving.value) {
      return
    }
    saving.value = true
    try {
      const payload = serialize()
      const definition = workflowId.value
        ? await updateWorkflow(workflowId.value, payload)
        : await createWorkflow(payload)
      hydrate(definition)
      await loadList()
    } finally {
      saving.value = false
    }
  }

  async function load(id: string) {
    if (!id || loading.value) {
      return
    }
    loading.value = true
    try {
      const definition = await getWorkflow(id)
      hydrate(definition)
      await loadList()
    } finally {
      loading.value = false
    }
  }

  async function loadList() {
    const result: PageResult<WorkflowDefinitionDTO> = await listWorkflows()
    workflowList.value = result.items
  }

  function reset() {
    nodes.value = []
    edges.value = []
    selectedNodeId.value = undefined
    workflowId.value = undefined
    workflowName.value = '未命名工作流'
  }

  return {
    nodes,
    edges,
    selectedNode,
    workflowId,
    workflowName,
    saving,
    loading,
    workflowList,
    addNode,
    updateNodeConfig,
    updateNodeStatus,
    updateNodeSchema,
    onNodesChange,
    onConnect,
    onNodeClick,
    getUpstreamNode,
    serialize,
    hydrate,
    save,
    load,
    loadList,
    reset,
  }
})
