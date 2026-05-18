import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Connection, NodeChange, XYPosition } from '@vue-flow/core'
import type {
  DatasetDTO,
  FieldSchemaDTO,
  NodeDebugRequestDTO,
  NodeMetaDTO,
  NodeResultDTO,
  PageResult,
  SchemaInferResultDTO,
  WorkflowDefinitionDTO,
  WorkflowEdgeDTO,
  WorkflowPositionDTO,
  WorkflowSaveRequestDTO,
} from '@/types/contract'
import type { AnalysisNodeStatus, WorkflowEdge, WorkflowNode } from '@/types/workflow'
import { buildNodePreview, createDefaultNodeConfig } from '@/utils/node-preview'
import { createWorkflow, getWorkflow, listWorkflows, updateWorkflow } from '@/api/workflow'
import { runNodeDebug as runNodeDebugApi } from '@/api/node-debug'

/**
 * 工作流编辑器核心 Pinia Store。
 *
 * 职责：
 * - 管理画布节点（nodes）和边（edges）的响应式状态
 * - 节点配置变更（updateNodeConfig）和状态流转（updateNodeSchema / updateNodeStatus）
 * - Schema 沿边传播（propagateSchemaFrom）：节点执行后将输出 Schema 推送给下游节点，
 *   驱动下游字段选择器自动填充候选字段
 * - 持久化（save / load / loadList）：与后端 REST API 同步工作流定义
 * - 单节点调试（runNodeDebug）：提交单节点执行请求，成功后更新 Schema 并传播
 *
 * 使用示例：
 * ```ts
 * const store = useWorkflowStore()
 * store.addNode(meta, { x: 200, y: 300 })
 * await store.save()
 * ```
 */

/** 生成节点唯一 ID（格式：nodeType-随机6位字母数字） */
function createNodeId(nodeType: string) {
  return `${nodeType}-${Math.random().toString(36).slice(2, 8)}`
}

/**
 * 从调试运行的数据集结果推断 Schema。
 *
 * 优先级：
 * 1. 使用后端返回的 dataset.schema.fields（最准确）
 * 2. 从 dataset.columns 数组构造（兼容旧格式）
 * 3. 从第一行数据的 key 列表构造（最后兜底）
 *
 * @param dataset 节点调试执行返回的数据集
 * @param nodeId  节点 ID，用于生成唯一 schemaId
 * @returns 推断出的 SchemaInferResultDTO，无法推断时返回 null
 */
function inferSchemaFromDataset(dataset: DatasetDTO, nodeId: string): SchemaInferResultDTO | null {
  // Use schema.fields provided by backend
  if (dataset.schema?.fields?.length) {
    return {
      protocolVersion: '1',
      schemaId: `debug-${nodeId}-${Date.now()}`,
      schemaVersion: '1',
      kind: 'DATASET',
      fields: dataset.schema.fields,
    }
  }
  // Derive from columns array
  const columns = dataset.columns ?? []
  if (columns.length > 0) {
    const fields: FieldSchemaDTO[] = columns.map((col, i) => {
      const name = String(col['field'] ?? col['name'] ?? Object.values(col)[0] ?? `col${i}`)
      return { fieldId: name, name, path: [name], valueType: 'STRING', nullable: true, displayName: name }
    })
    return { protocolVersion: '1', schemaId: `debug-${nodeId}-${Date.now()}`, schemaVersion: '1', kind: 'DATASET', fields }
  }
  // Derive from first row keys
  const rows = dataset.rows ?? []
  if (rows.length > 0) {
    const fields: FieldSchemaDTO[] = Object.keys(rows[0]).map(key => ({
      fieldId: key, name: key, path: [key], valueType: 'STRING' as FieldSchemaDTO['valueType'], nullable: true, displayName: key,
    }))
    return { protocolVersion: '1', schemaId: `debug-${nodeId}-${Date.now()}`, schemaVersion: '1', kind: 'DATASET', fields }
  }
  return null
}

/** 将节点数组转换为 {nodeId → {x, y}} 的位置映射，用于序列化到后端 */
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

  // Debug state
  const debugActiveTab = ref<'config' | 'input' | 'output'>('config')
  const debugLoadingNodeId = ref<string>()

  const selectedNode = computed(() => nodes.value.find(node => node.id === selectedNodeId.value))

  /** 向画布添加新节点，并自动选中该节点，默认位置 (120, 120) */
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

  /**
   * 更新节点配置，同步刷新节点预览摘要文本。
   * @param status 更新后的节点状态，默认置为 'draft'（配置未验证）
   */
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

    // If source has a debugResult but no schema yet, infer schema from it first
    const sourceNode = nodes.value.find(n => n.id === connection.source)
    if (sourceNode && !sourceNode.data.schema && sourceNode.data.debugResult?.result?.dataset) {
      const schema = inferSchemaFromDataset(sourceNode.data.debugResult.result.dataset, connection.source)
      if (schema) {
        updateNodeSchema(connection.source, schema)
      }
    }

    // Auto-propagate schema from source to target
    propagateSchemaFrom(connection.source)
  }

  /** Propagate source node's schema to all downstream nodes.
   *  @param force When true, overwrites existing schemas on downstream nodes (e.g. after a debug run transforms the schema). */
  function propagateSchemaFrom(sourceNodeId: string, force = false) {
    const sourceNode = nodes.value.find(n => n.id === sourceNodeId)
    if (!sourceNode?.data.schema) {
      return
    }
    // Find all edges where this node is the source
    const outgoingEdges = edges.value.filter(e => e.source === sourceNodeId)
    for (const edge of outgoingEdges) {
      const targetNode = nodes.value.find(n => n.id === edge.target)
      if (targetNode && (force || !targetNode.data.schema)) {
        // Propagate the schema downstream
        updateNodeSchema(edge.target, sourceNode.data.schema)
        // Recursively propagate further
        propagateSchemaFrom(edge.target, force)
      }
    }
  }

  function onNodeClick(payload: { node: WorkflowNode }) {
    selectedNodeId.value = payload.node.id
    debugActiveTab.value = 'config'
  }

  function getUpstreamNode(nodeId: string) {
    const edge = edges.value.find(item => item.target === nodeId)
    if (!edge) {
      return undefined
    }
    return nodes.value.find(item => item.id === edge.source)
  }

  /**
   * 将当前画布状态序列化为 API 请求体。
   * 丢弃前端运行时状态（status、schema、debugResult），只保留节点定义和位置。
   */
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

  /**
   * 从后端返回的工作流定义重建画布状态（反序列化）。
   * 节点位置从 definition.positions 映射中读取，缺省值为 (120, 120)。
   * 调用后节点状态统一重置为 'idle'，Schema 等运行时状态清空。
   */
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
          status: 'idle' as AnalysisNodeStatus,
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

  /**
   * 保存当前工作流到后端。
   * 若 workflowId 已存在则更新，否则创建新工作流。
   * 保存成功后以服务端返回值重新 hydrate（获取服务端分配的 workflowId 等字段）。
   */
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
    debugActiveTab.value = 'config'
    debugLoadingNodeId.value = undefined
  }

  function setDebugTab(tab: 'config' | 'input' | 'output') {
    debugActiveTab.value = tab
  }

  function setNodeMockInputs(nodeId: string, mockInputs: Record<string, unknown>) {
    nodes.value = nodes.value.map(node =>
      node.id === nodeId ? { ...node, data: { ...node.data, mockInputs } } : node,
    )
  }

  /**
   * 收集当前节点所有上游节点的实际执行结果，作为 upstreamMockInputs 传给后端。
   * 手动配置的 mockInputs 优先级更高，会覆盖自动收集的上游结果。
   */
  function buildUpstreamInputs(nodeId: string): Record<string, unknown> {
    const collected: Record<string, unknown> = {}
    // Collect actual debug results from all upstream nodes
    for (const edge of edges.value) {
      if (edge.target !== nodeId) continue
      const upstreamNode = nodes.value.find(n => n.id === edge.source)
      if (!upstreamNode) continue
      const result = upstreamNode.data.debugResult?.result
      if (result) {
        collected[edge.source] = result
      }
    }
    // Manual mockInputs override auto-collected results
    return { ...collected, ...(nodes.value.find(n => n.id === nodeId)?.data.mockInputs ?? {}) }
  }

  /**
   * 单节点调试执行。
   *
   * 执行完成后：
   * 1. 更新节点 debugResult 和 status（success / error）
   * 2. 若成功且结果含 dataset，从 dataset 推断 Schema 并沿边向下游传播（force=true）
   *    → 下游字段选择器可立即感知最新字段列表
   */
  async function runNodeDebug(nodeId: string) {
    const node = nodes.value.find(n => n.id === nodeId)
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
          nodeType: node.data.nodeType,
          config: node.data.config,
        },
        upstreamMockInputs: buildUpstreamInputs(nodeId),
      }
      const result: NodeResultDTO = await runNodeDebugApi(payload)
      nodes.value = nodes.value.map(n =>
        n.id === nodeId ? { ...n, data: { ...n.data, debugResult: result } } : n,
      )
      const nextStatus: AnalysisNodeStatus = result.status === 'SUCCESS' ? 'success'
        : result.status === 'FAILED' ? 'error' : 'running'
      updateNodeStatus(nodeId, nextStatus)

      // Update node output schema from debug result so downstream nodes see correct fields
      if (result.status === 'SUCCESS') {
        const dataset = result.result?.dataset
        if (dataset) {
          const schema = inferSchemaFromDataset(dataset, nodeId)
          if (schema) {
            updateNodeSchema(nodeId, schema)
            propagateSchemaFrom(nodeId, true) // force=true: overwrite downstream schemas
          }
        }
      }
    } catch (err) {
      const errorResult: NodeResultDTO = {
        nodeId,
        nodeType: node.data.nodeType,
        status: 'FAILED',
        error: { message: err instanceof Error ? err.message : '节点执行失败' },
      }
      nodes.value = nodes.value.map(n =>
        n.id === nodeId ? { ...n, data: { ...n.data, debugResult: errorResult } } : n,
      )
      updateNodeStatus(nodeId, 'error')
    } finally {
      debugLoadingNodeId.value = undefined
    }
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
    debugActiveTab,
    debugLoadingNodeId,
    addNode,
    updateNodeConfig,
    updateNodeStatus,
    updateNodeSchema,
    onNodesChange,
    onConnect,
    onNodeClick,
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
