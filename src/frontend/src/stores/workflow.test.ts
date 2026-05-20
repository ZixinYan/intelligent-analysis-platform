import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkflowStore } from '@/stores/workflow'
import type { NodeMetaDTO } from '@/types/contract'

vi.mock('@/api/workflow', () => ({
  createWorkflow: vi.fn(),
  getWorkflow: vi.fn(),
  listWorkflows: vi.fn(),
  updateWorkflow: vi.fn(),
}))

vi.mock('@/api/node-debug', () => ({
  runNodeDebug: vi.fn(),
}))

const baseMeta: NodeMetaDTO = {
  protocolVersion: '1',
  metadataVersion: '1',
  nodeType: 'sql_query',
  displayName: 'SQL 查询',
  description: '执行 SQL',
  category: 'QUERY',
  nodeVersion: '1',
  inputPorts: [],
  outputPorts: [],
  tags: [],
}

const aggregateMeta: NodeMetaDTO = {
  ...baseMeta,
  nodeType: 'aggregate',
  displayName: '聚合',
  category: 'COMPUTE',
}

const conditionMeta: NodeMetaDTO = {
  ...baseMeta,
  nodeType: 'condition',
  displayName: '条件分支',
  category: 'COMPUTE',
}

describe('useWorkflowStore insertions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('inserts node after source node', () => {
    const store = useWorkflowStore()
    const source = store.addNode(baseMeta, { x: 100, y: 100 })

    const inserted = store.insertNodeAfter({ sourceNodeId: source.id, meta: aggregateMeta, sourceHandle: 'output' })

    expect(inserted).toBeTruthy()
    expect(store.nodes).toHaveLength(2)
    expect(store.edges).toHaveLength(1)
    expect(store.edges[0]).toMatchObject({ source: source.id, target: inserted?.id, sourceHandle: 'output', targetHandle: 'input' })
  })

  it('inserts node before target node', () => {
    const store = useWorkflowStore()
    const target = store.addNode(baseMeta, { x: 400, y: 100 })

    const inserted = store.insertNodeBefore({ targetNodeId: target.id, meta: aggregateMeta, targetHandle: 'input' })

    expect(inserted).toBeTruthy()
    expect(store.edges).toHaveLength(1)
    expect(store.edges[0]).toMatchObject({ source: inserted?.id, target: target.id, sourceHandle: 'output', targetHandle: 'input' })
  })

  it('inserts node on edge and preserves condition on first segment', () => {
    const store = useWorkflowStore()
    const condition = store.addNode(conditionMeta, { x: 100, y: 100 })
    const target = store.addNode(baseMeta, { x: 420, y: 60 })
    store.onConnect({ source: condition.id, sourceHandle: 'true', target: target.id, targetHandle: 'input' })

    const originalEdgeId = store.edges[0].id
    const inserted = store.insertNodeOnEdge({ edgeId: originalEdgeId, meta: aggregateMeta })

    expect(inserted).toBeTruthy()
    expect(store.edges).toHaveLength(2)
    const firstSegment = store.edges.find(edge => edge.source === condition.id && edge.target === inserted?.id)
    const secondSegment = store.edges.find(edge => edge.source === inserted?.id && edge.target === target.id)
    expect(firstSegment).toMatchObject({ sourceHandle: 'true', condition: 'true', conditionLabel: 'true' })
    expect(secondSegment).toMatchObject({ sourceHandle: 'output', targetHandle: 'input' })
  })
})
