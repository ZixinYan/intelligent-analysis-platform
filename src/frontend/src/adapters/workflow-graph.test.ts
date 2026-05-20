import { describe, expect, it } from 'vitest'
import { getBusinessNodeType, getRawNodeType, graphToSaveRequest } from './workflow-graph'

describe('workflow graph node type helpers', () => {
  it('normalizes analysis node types to legacy business types', () => {
    expect(getBusinessNodeType({ type: 'analysis-aggregate' } as never)).toBe('aggregate')
    expect(getBusinessNodeType({ nodeType: 'analysis-sql-query' } as never)).toBe('sql_query')
    expect(getBusinessNodeType({ meta: { nodeType: 'analysis-chart-output' } } as never)).toBe('chart_output')
  })

  it('keeps legacy business types unchanged', () => {
    expect(getBusinessNodeType({ type: 'aggregate' } as never)).toBe('aggregate')
    expect(getBusinessNodeType({ nodeType: 'sql_query' } as never)).toBe('sql_query')
  })

  it('converts legacy business types to raw node types', () => {
    expect(getRawNodeType({ type: 'aggregate' } as never)).toBe('analysis-aggregate')
    expect(getRawNodeType({ nodeType: 'sql_query' } as never)).toBe('analysis-sql-query')
    expect(getRawNodeType({ meta: { nodeType: 'analysis-table-output' } } as never)).toBe('analysis-table-output')
  })

  it('saves raw node types for workflow payloads', () => {
    const payload = graphToSaveRequest({
      nodes: [{
        id: 'node-1',
        type: 'workflow-node',
        position: { x: 0, y: 0 },
        data: {
          type: 'aggregate',
          nodeType: 'aggregate',
          title: '聚合',
          config: {},
          status: 'idle',
        },
      }],
      edges: [],
      viewport: { x: 0, y: 0, zoom: 1 },
    }, 'test')

    expect(payload.nodes[0].nodeType).toBe('analysis-aggregate')
  })
})
