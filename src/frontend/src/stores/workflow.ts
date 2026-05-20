import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Connection, EdgeChange, NodeChange, XYPosition } from '@vue-flow/core'
import type {
  DatasetDTO,
  FieldSchemaDTO,
  NodeDebugRequestDTO,
  NodeMetaDTO,
  NodeResultDTO,
  PageResult,
  SchemaInferResultDTO,
  WorkflowDefinitionDTO,
} from '@/types/contract'
import type { AnalysisNodeStatus, WorkflowEdge, WorkflowGraph, WorkflowNode, WorkflowViewport } from '@/types/workflow'
import { createWorkflow, getWorkflow, listWorkflows, updateWorkflow } from '@/api/workflow'
import { runNodeDebug as runNodeDebugApi } from '@/api/node-debug'
import {
  createEmptyWorkflowGraph,
  DEFAULT_VIEWPORT,
  definitionDtoToGraph,
  getBusinessNodeType,
  graphToSaveRequest,
  WORKFLOW_RENDERER_NODE_TYPE,
} from '@/adapters/workflow-graph'
import { buildNodePreview, createDefaultNodeConfig } from '@/utils/node-preview'

function createNodeId(nodeType: string) {
  return `${nodeType}-${Math.random().toString(36).slice(2, 8)}`
}

function inferSchemaFromDataset(dataset: DatasetDTO, nodeId: string): SchemaInferResultDTO | null {
  if (dataset.schema?.fields?.length) {
    return {
      protocolVersion: '1',
      schemaId: `debug-${nodeId}-${Date.now()}`,
      schemaVersion: '1',
      kind: 'DATASET',
      fields: dataset.schema.fields,
    }
  }
  const columns = dataset.columns ?? []
  if (columns.length > 0) {
    const fields: FieldSchemaDTO[] = columns.map((col, i) => {
      const name = String(col['field'] ?? col['name'] ?? Object.values(col)[0] ?? `col${i}`)
      return { fieldId: name, name, path: [name], valueType: 'STRING', nullable: true, displayName: name }
    })
    return { protocolVersion: '1', schemaId: `debug-${nodeId}-${Date.now()}`, schemaVersion: '1', kind: 'DATASET', fields }
  }
  const rows = dataset.rows ?? []
  if (rows.length > 0) {
    const fields: FieldSchemaDTO[] = Object.keys(rows[0]).map(key => ({
      fieldId: key, name: key, path: [key], valueType: 'STRING' as FieldSchemaDTO['valueType'], nullable: true, displayName: key,
    }))
    return { protocolVersion: '1', schemaId: `debug-${nodeId}-${Date.now()}`, schemaVersion: '1', kind: 'DATASET', fields }
  }
  return null
}

const VIEWPORT_STORAGE_PREFIX = 'iap:workflow:viewport:'

function getViewportStorageKey(workflowId?: string) {
  return `${VIEWPORT_STORAGE_PREFIX}${workflowId || 'draft'}`
}

function loadStoredViewport(workflowId?: string): WorkflowViewport {
  if (typeof window === 'undefined') {
    return { ...DEFAULT_VIEWPORT }
  }
  try {
    const raw = window.localStorage.getItem(getViewportStorageKey(workflowId))
    if (!raw) {
      return { ...DEFAULT_VIEWPORT }
    }
    const parsed = JSON.parse(raw) as Partial<WorkflowViewport>
    return {
      x: typeof parsed.x === 'number' ? parsed.x : DEFAULT_VIEWPORT.x,
      y: typeof parsed.y === 'number' ? parsed.y : DEFAULT_VIEWPORT.y,
      zoom: typeof parsed.zoom === 'number' ? parsed.zoom : DEFAULT_VIEWPORT.zoom,
    }
  }
  catch {
    return { ...DEFAULT_VIEWPORT }
  }
}

function persistViewport(viewport: WorkflowViewport, workflowId?: string) {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(getViewportStorageKey(workflowId), JSON.stringify(viewport))
}

export const useWorkflowStore = defineStore('workflow', () => {
  const graph = ref<WorkflowGraph>(createEmptyWorkflowGraph())
  const selectedNodeId = ref<string>()
  const selectedNodeIds = ref<string[]>([])
  const selectedEdgeIds = ref<string[]>([])
  const workflowId = ref<string>()
  const workflowName = ref('未命名工作流')
  const saving = ref(false)
  const loading = ref(false)
  const workflowList = ref<WorkflowDefinitionDTO[]>([])
  const debugActiveTab = ref<'config' | 'input' | 'output'>('config')
  const debugLoadingNodeId = ref<string>()

  const nodes = computed(() => graph.value.nodes)
  const edges = computed(() => graph.value.edges)
  const viewport = computed(() => graph.value.viewport)
  const selectedNode = computed(() => graph.value.nodes.find(node => node.id === selectedNodeId.value))

  function setGraph(nextGraph: WorkflowGraph) {
    graph.value = nextGraph
    syncSelectionFromGraph()
  }

  function setViewport(nextViewport: WorkflowViewport) {
    graph.value = {
      ...graph.value,
      viewport: { ...nextViewport },
    }
    persistViewport(graph.value.viewport, workflowId.value)
  }

  function syncSelectionFromGraph() {
    selectedNodeIds.value = graph.value.nodes.filter(node => node.selected).map(node => node.id)
    selectedEdgeIds.value = graph.value.edges.filter(edge => edge.selected).map(edge => edge.id)
    selectedNodeId.value = selectedNodeIds.value.length === 1 && selectedEdgeIds.value.length === 0
      ? selectedNodeIds.value[0]
      : undefined
  }

  function setSingleNodeSelection(nodeId: string) {
    graph.value = {
      ...graph.value,
      nodes: graph.value.nodes.map(node => ({ ...node, selected: node.id === nodeId })),
      edges: graph.value.edges.map(edge => edge.selected ? { ...edge, selected: false } : edge),
    }
    syncSelectionFromGraph()
  }

  function clearSelection() {
    graph.value = {
      ...graph.value,
      nodes: graph.value.nodes.map(node => node.selected ? { ...node, selected: false } : node),
      edges: graph.value.edges.map(edge => edge.selected ? { ...edge, selected: false } : edge),
    }
    selectedNodeIds.value = []
    selectedEdgeIds.value = []
    selectedNodeId.value = undefined
  }

  function addNode(meta: NodeMetaDTO, position: XYPosition = { x: 120, y: 120 }) {
    const businessType = meta.nodeType
    const id = createNodeId(businessType)
    const config = createDefaultNodeConfig(meta)
    const node: WorkflowNode = {
      id,
      type: WORKFLOW_RENDERER_NODE_TYPE,
      position,
      selected: true,
      data: {
        type: businessType,
        nodeType: businessType,
        title: meta.displayName,
        meta,
        config,
        status: 'idle',
        preview: buildNodePreview(meta, config, businessType),
      },
    }
    graph.value = {
      ...graph.value,
      nodes: graph.value.nodes.map(item => item.selected ? { ...item, selected: false } : item).concat(node),
      edges: graph.value.edges.map(edge => edge.selected ? { ...edge, selected: false } : edge),
    }
    syncSelectionFromGraph()
  }

  function updateNode(nodeId: string, updater: (node: WorkflowNode) => WorkflowNode) {
    graph.value = {
      ...graph.value,
      nodes: graph.value.nodes.map(node => node.id === nodeId ? updater(node) : node),
    }
  }

  function updateNodeConfig(nodeId: string, config: Record<string, unknown>, status: AnalysisNodeStatus = 'draft') {
    updateNode(nodeId, node => ({
      ...node,
      data: {
        ...node.data,
        config,
        status,
        preview: buildNodePreview(node.data.meta, config, getBusinessNodeType(node)),
      },
    }))
  }

  function updateNodeStatus(nodeId: string, status: AnalysisNodeStatus, preview?: string[]) {
    updateNode(nodeId, node => ({
      ...node,
      data: {
        ...node.data,
        status,
        preview: preview ?? node.data.preview,
      },
    }))
  }

  function updateNodeSchema(nodeId: string, schema: WorkflowNode['data']['schema']) {
    updateNode(nodeId, node => ({
      ...node,
      data: {
        ...node.data,
        schema,
      },
    }))
  }

  function removeSelectedElements(nodeIds: string[], edgeIds: string[]) {
    if (nodeIds.length === 0 && edgeIds.length === 0) {
      return
    }
    const nodeIdSet = new Set(nodeIds)
    const edgeIdSet = new Set(edgeIds)
    graph.value = {
      ...graph.value,
      nodes: graph.value.nodes.filter(node => !nodeIdSet.has(node.id)),
      edges: graph.value.edges.filter(edge => !edgeIdSet.has(edge.id) && !nodeIdSet.has(edge.source) && !nodeIdSet.has(edge.target)),
    }
    syncSelectionFromGraph()
  }

  function onNodesChange(changes: NodeChange[]) {
    let nextNodes = graph.value.nodes
    let nextEdges = graph.value.edges
    let changed = false

    for (const change of changes) {
      if (change.type === 'position' && change.position) {
        nextNodes = nextNodes.map(node => node.id === change.id ? { ...node, position: change.position } : node)
        changed = true
      }
      if (change.type === 'remove') {
        nextNodes = nextNodes.filter(node => node.id !== change.id)
        nextEdges = nextEdges.filter(edge => edge.source !== change.id && edge.target !== change.id)
        changed = true
      }
      if (change.type === 'select') {
        nextNodes = nextNodes.map(node => node.id === change.id ? { ...node, selected: change.selected } : node)
        changed = true
      }
    }

    if (!changed) {
      return
    }

    graph.value = {
      ...graph.value,
      nodes: nextNodes,
      edges: nextEdges,
    }
    syncSelectionFromGraph()
  }

  function onEdgesChange(changes: EdgeChange[]) {
    let nextEdges: WorkflowEdge[] = graph.value.edges
    let changed = false

    for (const change of changes) {
      if (change.type === 'remove') {
        nextEdges = nextEdges.filter(edge => edge.id !== change.id)
        changed = true
      }
      if (change.type === 'select') {
        nextEdges = nextEdges.map(edge => edge.id === change.id ? { ...edge, selected: change.selected } : edge)
        changed = true
      }
    }

    if (!changed) {
      return
    }

    graph.value = {
      ...graph.value,
      edges: nextEdges,
    }
    syncSelectionFromGraph()
  }

  function deleteSelection() {
    const nodeIds = selectedNodeIds.value.length ? selectedNodeIds.value : selectedNodeId.value ? [selectedNodeId.value] : []
    removeSelectedElements(nodeIds, selectedEdgeIds.value)
  }

  function onConnect(connection: Connection) {
    if (!connection.source || !connection.target) {
      return
    }
    const condition = connection.sourceHandle === 'true' || connection.sourceHandle === 'false'
      ? connection.sourceHandle
      : undefined
    graph.value = {
      ...graph.value,
      edges: [
        ...graph.value.edges.map(edge => edge.selected ? { ...edge, selected: false } : edge),
        {
          id: `${connection.source}-${connection.target}-${Date.now()}`,
          source: connection.source,
          target: connection.target,
          sourceHandle: connection.sourceHandle,
          targetHandle: connection.targetHandle,
          animated: false,
          condition,
          conditionLabel: condition,
        },
      ],
    }

    const sourceNode = graph.value.nodes.find(n => n.id === connection.source)
    if (sourceNode && !sourceNode.data.schema && sourceNode.data.debugResult?.result?.dataset) {
      const schema = inferSchemaFromDataset(sourceNode.data.debugResult.result.dataset, connection.source)
      if (schema) {
        updateNodeSchema(connection.source, schema)
      }
    }
    propagateSchemaFrom(connection.source)
    syncSelectionFromGraph()
  }

  function propagateSchemaFrom(sourceNodeId: string, force = false) {
    const sourceNode = graph.value.nodes.find(n => n.id === sourceNodeId)
    if (!sourceNode?.data.schema) {
      return
    }
    const outgoingEdges = graph.value.edges.filter(edge => edge.source === sourceNodeId)
    for (const edge of outgoingEdges) {
      const targetNode = graph.value.nodes.find(node => node.id === edge.target)
      if (targetNode && (force || !targetNode.data.schema)) {
        updateNodeSchema(edge.target, sourceNode.data.schema)
        propagateSchemaFrom(edge.target, force)
      }
    }
  }

  function onNodeClick(payload: { node: WorkflowNode }) {
    if (!selectedNodeIds.value.includes(payload.node.id) || selectedNodeIds.value.length !== 1 || selectedEdgeIds.value.length > 0) {
      setSingleNodeSelection(payload.node.id)
    }
    debugActiveTab.value = 'config'
  }

  function onPaneClick() {
    clearSelection()
  }

  function getUpstreamNode(nodeId: string) {
    const edge = graph.value.edges.find(item => item.target === nodeId)
    if (!edge) {
      return undefined
    }
    return graph.value.nodes.find(item => item.id === edge.source)
  }

  function serialize() {
    return graphToSaveRequest(graph.value, workflowName.value)
  }

  function hydrate(definition: WorkflowDefinitionDTO) {
    workflowId.value = definition.workflowId
    workflowName.value = definition.workflowName || '未命名工作流'
    setGraph(definitionDtoToGraph(definition, loadStoredViewport(definition.workflowId)))
    selectedNodeId.value = undefined
    selectedNodeIds.value = []
    selectedEdgeIds.value = []
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
    }
    finally {
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
    }
    finally {
      loading.value = false
    }
  }

  async function loadList() {
    const result: PageResult<WorkflowDefinitionDTO> = await listWorkflows()
    workflowList.value = result.items
  }

  function reset() {
    setGraph(createEmptyWorkflowGraph())
    selectedNodeId.value = undefined
    selectedNodeIds.value = []
    selectedEdgeIds.value = []
    workflowId.value = undefined
    workflowName.value = '未命名工作流'
    debugActiveTab.value = 'config'
    debugLoadingNodeId.value = undefined
  }

  function setDebugTab(tab: 'config' | 'input' | 'output') {
    debugActiveTab.value = tab
  }

  function setNodeMockInputs(nodeId: string, mockInputs: Record<string, unknown>) {
    updateNode(nodeId, node => ({ ...node, data: { ...node.data, mockInputs } }))
  }

  function buildUpstreamInputs(nodeId: string): Record<string, unknown> {
    const collected: Record<string, unknown> = {}
    for (const edge of graph.value.edges) {
      if (edge.target !== nodeId) continue
      const upstreamNode = graph.value.nodes.find(n => n.id === edge.source)
      if (!upstreamNode) continue
      const result = upstreamNode.data.debugResult?.result
      if (result) {
        collected[edge.source] = result
      }
    }
    return { ...collected, ...(graph.value.nodes.find(n => n.id === nodeId)?.data.mockInputs ?? {}) }
  }

  async function runNodeDebug(nodeId: string) {
    const node = graph.value.nodes.find(n => n.id === nodeId)
    if (!node) return
    debugActiveTab.value = 'output'
    selectedNodeId.value = nodeId
    debugLoadingNodeId.value = nodeId
    updateNodeStatus(nodeId, 'running')
    try {
      const payload: NodeDebugRequestDTO = {
        nodeId,
        node: {
          nodeId,
          nodeType: getBusinessNodeType(node),
          config: node.data.config,
        },
        upstreamMockInputs: buildUpstreamInputs(nodeId),
      }
      const result: NodeResultDTO = await runNodeDebugApi(payload)
      updateNode(nodeId, n => ({ ...n, data: { ...n.data, debugResult: result } }))
      const nextStatus: AnalysisNodeStatus = result.status === 'SUCCESS' ? 'success'
        : result.status === 'FAILED' ? 'error' : 'running'
      updateNodeStatus(nodeId, nextStatus)
      if (result.status === 'SUCCESS') {
        const dataset = result.result?.dataset
        if (dataset) {
          const schema = inferSchemaFromDataset(dataset, nodeId)
          if (schema) {
            updateNodeSchema(nodeId, schema)
            propagateSchemaFrom(nodeId, true)
          }
        }
      }
    }
    catch (err) {
      const errorResult: NodeResultDTO = {
        nodeId,
        nodeType: getBusinessNodeType(node),
        status: 'FAILED',
        error: { message: err instanceof Error ? err.message : '节点执行失败' },
      }
      updateNode(nodeId, n => ({ ...n, data: { ...n.data, debugResult: errorResult } }))
      updateNodeStatus(nodeId, 'error')
    }
    finally {
      debugLoadingNodeId.value = undefined
    }
  }

  return {
    graph,
    nodes,
    edges,
    viewport,
    selectedNode,
    selectedNodeIds,
    selectedEdgeIds,
    workflowId,
    workflowName,
    saving,
    loading,
    workflowList,
    debugActiveTab,
    debugLoadingNodeId,
    addNode,
    setGraph,
    setViewport,
    updateNodeConfig,
    updateNodeStatus,
    updateNodeSchema,
    onNodesChange,
    onEdgesChange,
    onConnect,
    onNodeClick,
    onPaneClick,
    clearSelection,
    deleteSelection,
    setSingleNodeSelection,
    getUpstreamNode,
    propagateSchemaFrom,
    buildUpstreamInputs,
    serialize,
    hydrate,
    save,
    load,
    loadList,
    reset,
    setDebugTab,
    setNodeMockInputs,
    runNodeDebug,
  }
})
