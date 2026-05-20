import { describe, expect, it } from 'vitest'
import { getBusinessNodeType } from './workflow-graph'

describe('getBusinessNodeType', () => {
  it('normalizes analysis node types to legacy business types', () => {
    expect(getBusinessNodeType({ type: 'analysis-aggregate' } as never)).toBe('aggregate')
    expect(getBusinessNodeType({ nodeType: 'analysis-sql-query' } as never)).toBe('sql_query')
    expect(getBusinessNodeType({ meta: { nodeType: 'analysis-chart-output' } } as never)).toBe('chart_output')
  })

  it('keeps legacy business types unchanged', () => {
    expect(getBusinessNodeType({ type: 'aggregate' } as never)).toBe('aggregate')
    expect(getBusinessNodeType({ nodeType: 'sql_query' } as never)).toBe('sql_query')
  })
})
