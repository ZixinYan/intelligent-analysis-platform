import { describe, expect, it } from 'vitest'
import { resolveRendererModel } from '@/components/output/renderer'

describe('resolveRendererModel', () => {
  it('maps chart payload for runtime renderer', () => {
    const model = resolveRendererModel({
      kind: 'CHART',
      chart: {
        title: '趋势图',
        chartType: 'LINE',
        data: {
          categories: ['Mon', 'Tue'],
          series: [{ name: '访问量', data: [12, 18] }],
        },
        option: {
          legend: true,
          tooltip: true,
        },
      },
    })

    expect(model.kind).toBe('chart')
    if (model.kind !== 'chart') {
      return
    }
    expect(model.chartType).toBe('LINE')
    expect(model.series[0].data).toEqual([12, 18])
    expect(model.notices).toHaveLength(0)
  })

  it('maps dataset payload to table renderer in preview mode', () => {
    const model = resolveRendererModel({
      kind: 'DATASET',
      dataset: {
        rows: [{ city: '北京', uv: 100 }],
        total: 10,
      },
    }, 'preview')

    expect(model.kind).toBe('table')
    if (model.kind !== 'table') {
      return
    }
    expect(model.columns.map(item => item.key)).toEqual(['city', 'uv'])
    expect(model.partial).toBe(true)
    expect(model.notices[0]?.message).toContain('预览模式')
  })

  it('keeps chart renderable without categories', () => {
    const model = resolveRendererModel({
      kind: 'CHART',
      chart: {
        chartType: 'SCATTER',
        data: {
          series: [{ name: '点位', data: [3, 8, 13] }],
        },
      },
    })

    expect(model.kind).toBe('chart')
    if (model.kind !== 'chart') {
      return
    }
    expect(model.empty).toBe(false)
    expect(model.categories).toEqual([])
  })

  it('maps query result wrapper payload', () => {
    const model = resolveRendererModel({
      queryId: 'q-1',
      status: 'SUCCESS',
      result: {
        kind: 'TABLE',
        table: {
          title: '明细',
          columns: [{ field: 'name', label: '名称' }],
          rows: [{ name: '样例' }],
        },
      },
    })

    expect(model.kind).toBe('table')
    if (model.kind !== 'table') {
      return
    }
    expect(model.rows).toEqual([{ name: '样例' }])
  })
})
